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

    // --- CONFIGURATION DU MOTEUR ---
    private static final double WEIGHT_H2H = 0.05;            // Impact Historique
    private static final double HOME_ADVANTAGE_BASE = 1.15;   // Avantage domicile par défaut
    private static final double ELO_DIVISOR = 1000.0;         // Facteur de lissage Elo
    private static final double MAX_FINISHING_CORRECTION = 1.25; // Plafond Chance/Efficacité
    private static final double TIME_DECAY_CONSTANT = 60.0;   // Plus c'est bas, plus les matchs récents comptent

    // --- ENTREE PRINCIPALE ---
    public PredictionResult calculateMatchPrediction(MatchAnalysis match,
            List<MatchAnalysis> h2hHistory,
            List<MatchAnalysis> homeHistory,
            List<MatchAnalysis> awayHistory,
            double leagueAvgGoals) {

        // 1. ANALYSE DE PERFORMANCE
        TeamPerformance homePerf = analyzeTeamPerformance(match.getHomeTeam(), homeHistory, leagueAvgGoals, true, match.getMatchDate());
        TeamPerformance awayPerf = analyzeTeamPerformance(match.getAwayTeam(), awayHistory, leagueAvgGoals, false, match.getMatchDate());

        // 2. CALCUL ESPERANCE DE BUTS (RAW)
        double rawHomeExp = homePerf.attackRating * awayPerf.defenseRating * leagueAvgGoals;
        double rawAwayExp = awayPerf.attackRating * homePerf.defenseRating * leagueAvgGoals;

        // 3. FACTEURS DYNAMIQUES
        double homeAdvantage = calculateLeagueHomeAdvantage(match.getHomeTeam().getLeague());
        double awayDisadvantage = 1.0 / (homeAdvantage > 0 ? homeAdvantage : 1.0);

        double homeFinishing = Math.min(homePerf.finishingEfficiency, MAX_FINISHING_CORRECTION);
        double awayFinishing = Math.min(awayPerf.finishingEfficiency, MAX_FINISHING_CORRECTION);

        // 4. CALCUL LAMBDAS FINAUX
        double homeLambda = rawHomeExp * homeFinishing * homeAdvantage;
        double awayLambda = rawAwayExp * awayFinishing * awayDisadvantage;

        // 5. AJUSTEMENT ELO
        double eloDiff = match.getHomeTeam().getEloRating() - match.getAwayTeam().getEloRating();
        double eloMultiplier = Math.pow(10, eloDiff / ELO_DIVISOR);
        homeLambda *= eloMultiplier;

        // 6. AJUSTEMENT HISTORIQUE
        if (h2hHistory != null && !h2hHistory.isEmpty()) {
            double h2hFactor = calculateH2HFactor(match.getHomeTeam().getName(), h2hHistory);
            homeLambda *= (1.0 + h2hFactor);
        }

        // 7. CONTEXTE & METEO (CORRECTION ICI)
        homeLambda = applyContextualFactors(homeLambda, true, match);
        awayLambda = applyContextualFactors(awayLambda, false, match);

        // On récupère le facteur météo et on l'applique
        double weatherFactor = calculateWeatherFactor(match);
        homeLambda *= weatherFactor;
        awayLambda *= weatherFactor;

        // 8. SIMULATION
        return simulateMatch(homeLambda, awayLambda, match, homePerf, awayPerf);
    }

    // --- SOUS-SYSTEMES ---

    private double calculateLeagueHomeAdvantage(League league) {
        if (league == null || league.getPercentHomeWin() == null || league.getPercentAwayWin() == null) {
            return HOME_ADVANTAGE_BASE;
        }
        return 1.0 + ((league.getPercentHomeWin() - league.getPercentAwayWin()) / 100.0);
    }

    private TeamPerformance analyzeTeamPerformance(Team team, List<MatchAnalysis> history, double leagueAvg, boolean isHomeAnalysis, LocalDateTime targetDate) {
        if (history == null || history.isEmpty()) return new TeamPerformance(1.0, 1.0, 1.0, 0.5, 1.0);

        double sumXgFor = 0.0, sumXgAgainst = 0.0;
        double sumGoalsFor = 0.0;
        double totalWeight = 0.0;
        double volatilitySum = 0.0;

        for (MatchAnalysis m : history) {
            if (m.getMatchDate() == null) continue;

            // Décroissance exponentielle utilisant la constante TIME_DECAY_CONSTANT
            long daysAgo = Math.abs(Duration.between(targetDate, m.getMatchDate()).toDays());
            double timeWeight = Math.exp(-daysAgo / TIME_DECAY_CONSTANT);

            boolean wasHome = m.getHomeTeam().equals(team);
            double contextWeight = (wasHome == isHomeAnalysis) ? 1.10 : 0.90;

            double finalWeight = timeWeight * contextWeight;

            MatchDetailStats myStats = wasHome ? m.getHomeMatchStats() : m.getAwayMatchStats();
            MatchDetailStats oppStats = wasHome ? m.getAwayMatchStats() : m.getHomeMatchStats();

            int goalsF = safeInt(wasHome ? m.getHomeScore() : m.getAwayScore());
            int goalsA = safeInt(wasHome ? m.getAwayScore() : m.getHomeScore());
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

    /**
     * Calcule un facteur multiplicateur basé sur la météo.
     * Retourne 1.0 si pas d'impact ou pas de données.
     * Retourne < 1.0 si conditions difficiles.
     */
    private double calculateWeatherFactor(MatchAnalysis match) {
        var wOpt = weatherService.getMatchWeather(match.getHomeTeam().getLatitude(), match.getHomeTeam().getLongitude(), match.getMatchDate().toString());

        double factor = 1.0;

        if (wOpt.isPresent()) {
            var w = wOpt.get();
            // Vent > 30km/h réduit la précision et le nombre de buts attendus
            if (w.windSpeed() > 30.0) {
                factor *= 0.90;
            }
            // La pluie peut avoir un impact, ici, on reste neutre pour l'instant (1.0)
            // sauf si on veut réduire légèrement le score global
            // if (w.isRaining()) factor *= 0.98;
        }

        return factor;
    }

    private PredictionResult simulateMatch(double lambdaHome, double lambdaAway, MatchAnalysis match, TeamPerformance homePerf, TeamPerformance awayPerf) {
        double probHome = 0.0, probDraw = 0.0, probAway = 0.0;
        double probOver25 = 0.0, probBTTS = 0.0;
        double probUnder1_5 = 0.0, probUnder2_5 = 0.0, probOver3_5 = 0.0, probBTTS_No = 0.0;
        double probHomeOver05 = 0.0, probHomeOver15 = 0.0;
        double probAwayOver05 = 0.0, probAwayOver15 = 0.0;

        double maxProb = -1.0;
        String exactScore = "0-0";

        double gameChaos = (homePerf.volatility + awayPerf.volatility) / 2.0;

        for (int h = 0; h <= 9; h++) {
            for (int a = 0; a <= 9; a++) {
                double p = poisson(h, lambdaHome) * poisson(a, lambdaAway);
                p = applyDixonColes(p, h, a, lambdaHome, lambdaAway);

                if (h == a && gameChaos > 1.2) p *= 0.92;

                if (h > a) probHome += p;
                else if (h == a) probDraw += p;
                else probAway += p;

                if ((h + a) > 2.5) probOver25 += p;
                if (h > 0 && a > 0) probBTTS += p;
                else probBTTS_No += p;

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
        // Normalisation
        probHome = (probHome / total) * 100;
        probDraw = (probDraw / total) * 100;
        probAway = (probAway / total) * 100;
        double exactScoreProb = (maxProb / total) * 100;

        double kHome = calculateKelly(probHome / 100, match.getOdds1());
        double kDraw = calculateKelly(probDraw / 100, match.getOddsN());
        double kAway = calculateKelly(probAway / 100, match.getOdds2());

        String valueSide = null;
        if (kHome > 0) valueSide = "HOME";
        else if (kAway > 0) valueSide = "AWAY";
        else if (kDraw > 0) valueSide = "DRAW";

        double kOv25 = calculateKelly((probOver25 / total), match.getOddsOver25());
        double kBTTS = calculateKelly((probBTTS / total), match.getOddsBTTSYes());

        return new PredictionResult(
                round(probHome), round(probDraw), round(probAway),
                0.0, 0.0,
                round(lambdaHome), round(lambdaAway),
                round((probOver25/total)*100), round((probUnder2_5/total)*100), round((probBTTS/total)*100),
                round(probHome + probDraw), round(probDraw + probAway), round(probHome + probAway),
                kHome, kDraw, kAway, valueSide,
                round((probHomeOver05/total)*100), round((probHomeOver15/total)*100),
                round((probAwayOver05/total)*100), round((probAwayOver15/total)*100),
                0.0, kOv25, kBTTS,
                exactScore, round(exactScoreProb),
                round((probUnder1_5/total)*100), round((probUnder2_5/total)*100),
                round((probOver3_5/total)*100), round((probBTTS_No/total)*100)
        );
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

    private double calculateH2HFactor(String homeName, List<MatchAnalysis> h2h) {
        double factor = 0.0;
        int limit = Math.min(h2h.size(), 5);
        for (int i = 0; i < limit; i++) {
            MatchAnalysis m = h2h.get(i);
            if (m.getHomeScore() == null) continue;
            boolean homeWon = (m.getHomeTeam().getName().equals(homeName) && m.getHomeScore() > m.getAwayScore()) ||
                    (m.getAwayTeam().getName().equals(homeName) && m.getAwayScore() > m.getHomeScore());
            if (homeWon) factor += WEIGHT_H2H;
            else factor -= (WEIGHT_H2H / 2);
        }
        return factor;
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

    private int safeInt(Integer val) { return val == null ? 0 : val; }
    private double round(double val) { return Math.round(val * 100.0) / 100.0; }

    private record TeamPerformance(
            double attackRating,
            double defenseRating,
            double finishingEfficiency,
            double dominance,
            double volatility
    ) {}
}
