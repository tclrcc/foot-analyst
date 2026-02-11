package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionEngineService {

    private final WeatherService weatherService;

    // --- CONFIGURATION ---
    private static final double WEIGHT_H2H = 0.08;            // Augmenté (0.05 -> 0.08) car l'historique est plus profond
    private static final double HOME_ADVANTAGE_BASE = 1.15;
    private static final double ELO_DIVISOR = 400.0;
    private static final double MAX_FINISHING_CORRECTION = 1.25;
    private static final double TIME_DECAY_CONSTANT = 60.0;
    private static final double HYBRID_WEIGHT_POISSON = 0.60; // 60% Stats / 40% Elo
    private static final double HYBRID_WEIGHT_ELO = 0.40;

    public PredictionResult calculateMatchPrediction(MatchAnalysis match,
            List<MatchAnalysis> h2hHistory,
            List<MatchAnalysis> homeHistory,
            List<MatchAnalysis> awayHistory,
            double leagueAvgGoals) {

        // 0. ELO Probabilities (Force brute long terme)
        double eloDiff = match.getHomeTeam().getEloRating() - match.getAwayTeam().getEloRating();
        double eloProbHome = 1.0 / (1.0 + Math.pow(10, (-eloDiff - 100.0) / ELO_DIVISOR));
        double eloProbAway = 1.0 / (1.0 + Math.pow(10, (eloDiff + 100.0) / ELO_DIVISOR));

        // 1. Performance Analysis (Dynamique actuelle - 10 derniers matchs)
        TeamPerformance homePerf = analyzeTeamPerformance(match.getHomeTeam(), homeHistory, leagueAvgGoals, true, match.getMatchDate());
        TeamPerformance awayPerf = analyzeTeamPerformance(match.getAwayTeam(), awayHistory, leagueAvgGoals, false, match.getMatchDate());

        // 2. Expected Goals (Raw)
        double rawHomeExp = homePerf.attackRating() * awayPerf.defenseRating() * leagueAvgGoals;
        double rawAwayExp = awayPerf.attackRating() * homePerf.defenseRating() * leagueAvgGoals;

        // 3. Dynamic Factors
        double homeAdvantage = calculateLeagueHomeAdvantage(match.getHomeTeam().getLeague());
        double awayDisadvantage = 1.0 / (homeAdvantage > 0 ? homeAdvantage : 1.0);

        double homeFinishing = Math.min(homePerf.finishingEfficiency(), MAX_FINISHING_CORRECTION);
        double awayFinishing = Math.min(awayPerf.finishingEfficiency(), MAX_FINISHING_CORRECTION);

        // 4. Lambdas Calculation
        double homeLambda = rawHomeExp * homeFinishing * homeAdvantage;
        double awayLambda = rawAwayExp * awayFinishing * awayDisadvantage;

        // 5. Elo Adjustment on Goals
        double eloMultiplier = Math.pow(10, eloDiff / 1000.0);
        homeLambda *= eloMultiplier;

        // 6. H2H Adjustment (Deep History)
        if (h2hHistory != null && !h2hHistory.isEmpty()) {
            double h2hFactor = calculateH2HFactor(match.getHomeTeam(), h2hHistory);
            homeLambda *= (1.0 + h2hFactor);
        }

        // 7. Context & Weather
        homeLambda = applyContextualFactors(homeLambda, true, match);
        awayLambda = applyContextualFactors(awayLambda, false, match);
        double weatherFactor = calculateWeatherFactor(match);
        homeLambda *= weatherFactor;
        awayLambda *= weatherFactor;

        // Volatility Penalty (Si l'équipe est instable sur ses 10 derniers matchs)
        double volatility = (homePerf.volatility() + awayPerf.volatility()) / 2.0;
        if (volatility > 1.4) {
            // On rapproche les espérances de la moyenne de la ligue (réduit la confiance)
            homeLambda = (homeLambda + leagueAvgGoals) / 2.0;
            awayLambda = (awayLambda + leagueAvgGoals) / 2.0;
        }

        // 8. Simulation (Poisson)
        PredictionResult poissonResult = simulateMatch(homeLambda, awayLambda, match, homePerf, awayPerf);

        // 9. Hybrid Fusion & Builder
        double finalProbHome = (poissonResult.getHomeWinProbability() * HYBRID_WEIGHT_POISSON) + (eloProbHome * 100.0 * HYBRID_WEIGHT_ELO);
        double finalProbAway = (poissonResult.getAwayWinProbability() * HYBRID_WEIGHT_POISSON) + (eloProbAway * 100.0 * HYBRID_WEIGHT_ELO);
        double finalProbDraw = 100.0 - finalProbHome - finalProbAway;

        return PredictionResult.builder()
                .homeWinProbability(round(finalProbHome))
                .drawProbability(round(finalProbDraw))
                .awayWinProbability(round(finalProbAway))

                .homePowerScore(poissonResult.getHomePowerScore())
                .awayPowerScore(poissonResult.getAwayPowerScore())

                .predictedHomeGoals(poissonResult.getPredictedHomeGoals())
                .predictedAwayGoals(poissonResult.getPredictedAwayGoals())

                .over2_5_Prob(poissonResult.getOver2_5_Prob())
                .under2_5_Prob(poissonResult.getUnder2_5_Prob())
                .bttsProb(poissonResult.getBttsProb())

                .doubleChance1N(poissonResult.getDoubleChance1N())
                .doubleChanceN2(poissonResult.getDoubleChanceN2())
                .doubleChance12(poissonResult.getDoubleChance12())

                .kellyStakeHome(poissonResult.getKellyStakeHome())
                .kellyStakeDraw(poissonResult.getKellyStakeDraw())
                .kellyStakeAway(poissonResult.getKellyStakeAway())
                .valueBetDetected(poissonResult.getValueBetDetected())

                .homeScoreOver0_5(poissonResult.getHomeScoreOver0_5())
                .homeScoreOver1_5(poissonResult.getHomeScoreOver1_5())
                .awayScoreOver0_5(poissonResult.getAwayScoreOver0_5())
                .awayScoreOver1_5(poissonResult.getAwayScoreOver1_5())

                .kellyOver1_5(poissonResult.getKellyOver1_5())
                .kellyOver2_5(poissonResult.getKellyOver2_5())
                .kellyBTTS(poissonResult.getKellyBTTS())

                .exactScore(poissonResult.getExactScore())
                .exactScoreProb(poissonResult.getExactScoreProb())

                .probUnder1_5(poissonResult.getProbUnder1_5())
                .probUnder2_5(poissonResult.getProbUnder2_5())
                .probOver3_5(poissonResult.getProbOver3_5())
                .probBTTS_No(poissonResult.getProbBTTS_No())

                .predictionCorrect(null)
                .brierScore(null)
                .rpsScore(null)

                .build();
    }

    // --- ANALYSE DYNAMIQUE (FORME) ---
    private TeamPerformance analyzeTeamPerformance(Team team, List<MatchAnalysis> history, double leagueAvg, boolean isHomeAnalysis, LocalDateTime targetDate) {
        if (history == null || history.isEmpty()) return new TeamPerformance(1.0, 1.0, 1.0, 0.5, 1.0);

        // OPTIMISATION : On ne prend que les 10 derniers matchs pour la "Forme"
        // Cela garantit que l'algo capte la dynamique récente (ex: changement de coach, blessures...)
        int limit = Math.min(history.size(), 10);

        double sumXgFor = 0.0, sumXgAgainst = 0.0;
        double sumGoalsFor = 0.0;
        double totalWeight = 0.0;
        double volatilitySum = 0.0;

        // Boucle limitée aux X derniers matchs
        for (int i = 0; i < limit; i++) {
            MatchAnalysis m = history.get(i);

            if (m.getMatchDate() == null) continue;
            long daysAgo = Math.abs(Duration.between(targetDate, m.getMatchDate()).toDays());

            // Time Decay : Plus c'est récent, plus ça compte
            double timeWeight = Math.exp(-daysAgo / TIME_DECAY_CONSTANT);

            boolean wasHome = m.getHomeTeam().equals(team);
            double contextWeight = (wasHome == isHomeAnalysis) ? 1.10 : 0.90; // Bonus si même contexte (Dom/Ext)
            double finalWeight = timeWeight * contextWeight;

            MatchDetailStats myStats = wasHome ? m.getHomeMatchStats() : m.getAwayMatchStats();
            MatchDetailStats oppStats = wasHome ? m.getAwayMatchStats() : m.getHomeMatchStats();

            int goalsF = safeInt(wasHome ? m.getHomeScore() : m.getAwayScore());
            int goalsA = safeInt(wasHome ? m.getAwayScore() : m.getHomeScore());

            // Estimation xG si non dispo
            double xgF = (myStats != null && myStats.getXG() != null) ? myStats.getXG() : Math.max(0.2, goalsF * 0.8);
            double xgA = (oppStats != null && oppStats.getXG() != null) ? oppStats.getXG() : Math.max(0.2, goalsA * 0.8);

            sumXgFor += xgF * finalWeight;
            sumXgAgainst += xgA * finalWeight;
            sumGoalsFor += goalsF * finalWeight;
            volatilitySum += (xgF + xgA) * finalWeight;
            totalWeight += finalWeight;
        }

        if (totalWeight == 0) return new TeamPerformance(1.0, 1.0, 1.0, 0.5, 1.0);

        double avgXgFor = sumXgFor / totalWeight;
        double avgXgAgainst = sumXgAgainst / totalWeight;
        double avgGoalsFor = sumGoalsFor / totalWeight;

        double attackRating = (leagueAvg > 0) ? (avgXgFor / leagueAvg) : 1.0;
        double defenseRating = (leagueAvg > 0) ? (avgXgAgainst / leagueAvg) : 1.0;
        double finishing = (avgXgFor > 0.2) ? (avgGoalsFor / avgXgFor) : 1.0;
        double volatility = (volatilitySum / totalWeight) / (leagueAvg * 2);

        return new TeamPerformance(attackRating, defenseRating, finishing, 0.5, volatility);
    }

    // --- ANALYSE HISTORIQUE (H2H TENDANCE) ---
    private double calculateH2HFactor(Team currentHomeTeam, List<MatchAnalysis> h2h) {
        double factor = 0.0;

        // On analyse jusqu'à 10 confrontations pour dégager une tendance lourde
        int limit = Math.min(h2h.size(), 10);

        for (int i = 0; i < limit; i++) {
            MatchAnalysis m = h2h.get(i);
            if (m.getHomeScore() == null) continue;

            // Pondération Décroissante : Le match le plus récent (i=0) pèse 100%
            // Le 10ème match (i=9) pèse ~10%
            double weight = (double)(limit - i) / limit;

            boolean homeWonMatch = m.getHomeScore() > m.getAwayScore();
            boolean awayWonMatch = m.getAwayScore() > m.getHomeScore();

            // Logique : Est-ce que "NOTRE" équipe domicile (currentHomeTeam) a gagné ce duel passé ?

            if (m.getHomeTeam().equals(currentHomeTeam) && homeWonMatch) {
                // Elle jouait à domicile et a gagné
                factor += (WEIGHT_H2H * weight);
            }
            else if (m.getAwayTeam().equals(currentHomeTeam) && awayWonMatch) {
                // Elle jouait à l'extérieur et a gagné (Bonus de force)
                factor += (WEIGHT_H2H * weight * 1.2);
            }
            else if ((m.getHomeTeam().equals(currentHomeTeam) && awayWonMatch) ||
                    (m.getAwayTeam().equals(currentHomeTeam) && homeWonMatch)) {
                // Elle a perdu ce duel
                factor -= (WEIGHT_H2H * weight);
            }
            // Si Nul, on ne fait rien (0.0). C'est neutre.
        }
        return factor;
    }

    private PredictionResult simulateMatch(double lambdaHome, double lambdaAway, MatchAnalysis match, TeamPerformance homePerf, TeamPerformance awayPerf) {
        double probHome = 0.0, probDraw = 0.0, probAway = 0.0;
        double probOver25 = 0.0, probBTTS = 0.0, probBTTS_No = 0.0;
        double probUnder1_5 = 0.0, probUnder2_5 = 0.0, probOver3_5 = 0.0;
        double probHomeOver05 = 0.0, probHomeOver15 = 0.0;
        double probAwayOver05 = 0.0, probAwayOver15 = 0.0;

        double maxProb = -1.0;
        String exactScore = "0-0";
        double gameChaos = (homePerf.volatility() + awayPerf.volatility()) / 2.0;

        for (int h = 0; h <= 9; h++) {
            for (int a = 0; a <= 9; a++) {
                double p = poisson(h, lambdaHome) * poisson(a, lambdaAway);
                p = applyDixonColes(p, h, a, lambdaHome, lambdaAway);

                if (h == a && gameChaos > 1.2) p *= 0.92; // Chaos réduit les nuls

                if (h > a) probHome += p;
                else if (h == a) probDraw += p;
                else probAway += p;

                if ((h + a) > 2.5) probOver25 += p;
                if (h > 0 && a > 0) probBTTS += p; else probBTTS_No += p;

                int totalGoals = h + a;
                if (totalGoals < 1.5) probUnder1_5 += p;
                if (totalGoals < 2.5) probUnder2_5 += p;
                if (totalGoals > 3.5) probOver3_5 += p;

                if(h > 0) probHomeOver05 += p; if(h > 1) probHomeOver15 += p;
                if(a > 0) probAwayOver05 += p; if(a > 1) probAwayOver15 += p;

                if (p > maxProb) { maxProb = p; exactScore = h + "-" + a; }
            }
        }

        double total = probHome + probDraw + probAway;
        probHome = (probHome / total) * 100;
        probDraw = (probDraw / total) * 100;
        probAway = (probAway / total) * 100;
        double exactScoreProb = (maxProb / total) * 100;

        // Kelly 1N2
        double kHome = calculateKelly(probHome / 100, match.getOdds1());
        double kDraw = calculateKelly(probDraw / 100, match.getOddsN());
        double kAway = calculateKelly(probAway / 100, match.getOdds2());

        String valueSide = null;
        if (kHome > 0) valueSide = "HOME";
        else if (kAway > 0) valueSide = "AWAY";
        else if (kDraw > 0) valueSide = "DRAW";

        // Kelly Goals
        double kOv25 = calculateKelly((probOver25 / total), match.getOddsOver25());
        double kBTTS = calculateKelly((probBTTS / total), match.getOddsBTTSYes());
        double kOv15 = 0.0;

        return PredictionResult.builder()
                .homeWinProbability(round(probHome))
                .drawProbability(round(probDraw))
                .awayWinProbability(round(probAway))
                .homePowerScore(0.0)
                .awayPowerScore(0.0)
                .predictedHomeGoals(round(lambdaHome))
                .predictedAwayGoals(round(lambdaAway))
                .over2_5_Prob(round((probOver25/total)*100))
                .under2_5_Prob(round((probUnder2_5/total)*100))
                .bttsProb(round((probBTTS/total)*100))
                .doubleChance1N(round(probHome + probDraw))
                .doubleChanceN2(round(probDraw + probAway))
                .doubleChance12(round(probHome + probAway))
                .kellyStakeHome(kHome)
                .kellyStakeDraw(kDraw)
                .kellyStakeAway(kAway)
                .valueBetDetected(valueSide)
                .homeScoreOver0_5(round((probHomeOver05/total)*100))
                .homeScoreOver1_5(round((probHomeOver15/total)*100))
                .awayScoreOver0_5(round((probAwayOver05/total)*100))
                .awayScoreOver1_5(round((probAwayOver15/total)*100))
                .kellyOver1_5(kOv15)
                .kellyOver2_5(kOv25)
                .kellyBTTS(kBTTS)
                .exactScore(exactScore)
                .exactScoreProb(round(exactScoreProb))
                .probUnder1_5(round((probUnder1_5/total)*100))
                .probUnder2_5(round((probUnder2_5/total)*100))
                .probOver3_5(round((probOver3_5/total)*100))
                .probBTTS_No(round((probBTTS_No/total)*100))
                .predictionCorrect(null)
                .brierScore(null)
                .rpsScore(null)
                .build();
    }

    // --- UTILS ---
    private double calculateLeagueHomeAdvantage(League league) {
        if (league == null || league.getPercentHomeWin() == null || league.getPercentAwayWin() == null) {
            return HOME_ADVANTAGE_BASE;
        }
        return 1.0 + ((league.getPercentHomeWin() - league.getPercentAwayWin()) / 100.0);
    }

    private double calculateWeatherFactor(MatchAnalysis match) {
        if (match.getMatchDate().isBefore(LocalDateTime.now())) return 1.0;
        var wOpt = weatherService.getMatchWeather(
                match.getHomeTeam().getLatitude(),
                match.getHomeTeam().getLongitude(),
                match.getMatchDate().toString()
        );
        double factor = 1.0;
        if (wOpt.isPresent()) {
            var w = wOpt.get();
            if (w.windSpeed() > 30.0) factor *= 0.90;
        }
        return factor;
    }

    private double calculateKelly(double prob, Double odds) {
        if (odds == null || odds <= 1.0) return 0.0;
        double b = odds - 1.0;
        double q = 1.0 - prob;
        double f = ((b * prob) - q) / b;
        return f > 0 ? round((f * 0.25) * 100.0) : 0.0;
    }

    private double poisson(int k, double lambda) {
        return (Math.pow(lambda, k) * Math.exp(-lambda)) / factorial(k);
    }

    private long factorial(int n) {
        if (n <= 1) return 1;
        long f = 1;
        for (int i = 2; i <= n; i++) f *= i;
        return f;
    }

    private double applyDixonColes(double p, int h, int a, double lh, double la) {
        double rho = -0.13;
        double adj = 1.0;
        if(h==0 && a==0) adj = 1.0 - (lh*la*rho);
        else if(h==0 && a==1) adj = 1.0 + (lh*rho);
        else if(h==1 && a==0) adj = 1.0 + (la*rho);
        else if(h==1 && a==1) adj = 1.0 - rho;
        return Math.max(0, p * adj);
    }

    private double applyContextualFactors(double lambda, boolean isHome, MatchAnalysis m) {
        double f = 1.0;
        if (isHome) {
            if (m.isHomeKeyPlayerMissing()) f -= 0.12;
            if (m.isHomeTired()) f -= 0.08;
        } else {
            if (m.isAwayKeyPlayerMissing()) f -= 0.12;
            if (m.isAwayNewCoach()) f += 0.05;
        }
        return lambda * f;
    }

    private double calculateLambda(Team home, Team away, double leagueAvg) {
        // Pro : lambda = alpha_domicile * beta_exterieur * gamma
        // Utilise Team.getAttackStrength() et Team.getDefenseStrength()
        double homeAdvantage = 1.15; // gamma calculé par MLE
        return home.getAttackStrength() * away.getDefenseStrength() * homeAdvantage;
    }

    private int safeInt(Integer val) { return val == null ? 0 : val; }
    private double round(double val) { return Math.round(val * 100.0) / 100.0; }

    public record TeamPerformance(
            double attackRating,
            double defenseRating,
            double finishingEfficiency,
            double dominance,
            double volatility
    ) {}
}
