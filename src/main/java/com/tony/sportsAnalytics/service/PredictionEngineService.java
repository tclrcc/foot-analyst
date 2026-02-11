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
    private final AdvancedPredictionService advancedPrediction;
    private final CalibrationService calibrationService;

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

        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();
        League league = home.getLeague();

        // 0. ELO Probabilities (Fondation long terme)
        double eloDiff = home.getEloRating() - away.getEloRating();
        double eloProbHome = 1.0 / (1.0 + Math.pow(10, (-eloDiff - 100.0) / ELO_DIVISOR));
        double eloProbAway = 1.0 / (1.0 + Math.pow(10, (eloDiff + 100.0) / ELO_DIVISOR));

        // 1. Dixon-Coles Lambdas (Force intrinsèque via MLE dynamique)
        // On utilise les paramètres gamma et alpha/beta calibrés par ParameterEstimationService
        double homeAdvantage = (league != null && league.getHomeAdvantageFactor() != null)
                ? league.getHomeAdvantageFactor() : HOME_ADVANTAGE_BASE;
        double homeLambda = home.getAttackStrength() * away.getDefenseStrength() * homeAdvantage;
        double awayLambda = away.getAttackStrength() * home.getDefenseStrength();

        // 2. Intégration des Expected Goals (xG) scrapés pour stabiliser les lambdas
        if (home.getCurrentStats() != null && home.getCurrentStats().getXG() != null) {
            homeLambda = (homeLambda + home.getCurrentStats().getXG()) / 2.0;
        }
        if (away.getCurrentStats() != null && away.getCurrentStats().getXG() != null) {
            awayLambda = (awayLambda + away.getCurrentStats().getXG()) / 2.0;
        }

        // 3. Ajustement Dynamique (Performance récente - Forme sur 10 matchs)
        TeamPerformance homePerf = analyzeTeamPerformance(home, homeHistory, leagueAvgGoals, true, match.getMatchDate());
        TeamPerformance awayPerf = analyzeTeamPerformance(away, awayHistory, leagueAvgGoals, false, match.getMatchDate());

        homeLambda *= Math.min(homePerf.finishingEfficiency(), MAX_FINISHING_CORRECTION);
        awayLambda *= Math.min(awayPerf.finishingEfficiency(), MAX_FINISHING_CORRECTION);

        // 4. H2H Adjustments (Historique profond pondéré)
        if (h2hHistory != null && !h2hHistory.isEmpty()) {
            homeLambda *= (1.0 + calculateH2HFactor(home, h2hHistory));
        }

        // 5. Tactical Overlays (Interaction Field Tilt / PPDA / Volatilité)
        homeLambda = applyTacticalOverlay(homeLambda, home, away, awayPerf.volatility());
        awayLambda = applyTacticalOverlay(awayLambda, away, home, homePerf.volatility());

        // 6. Impact Joueurs (Missing Players via Impact Score) & Contextuel
        homeLambda = applyContextualFactors(homeLambda, true, match);
        awayLambda = applyContextualFactors(awayLambda, false, match);

        // Météo (Vent > 30km/h réduit l'efficacité)
        double weatherFactor = calculateWeatherFactor(match);
        homeLambda *= weatherFactor;
        awayLambda *= weatherFactor;

        // 7. Simulation Dixon-Coles Pro (Utilisation du Rho dynamique de la ligue)
        double currentRho = (league != null && league.getRho() != null) ? league.getRho() : -0.13;
        PredictionResult poissonResult = simulateMatchPro(homeLambda, awayLambda, match, homePerf, awayPerf, currentRho);

        // 8. Hybrid Fusion (Stats Dixon-Coles 60% / Elo 40%)
        double rawHome = (poissonResult.getHomeWinProbability() * HYBRID_WEIGHT_POISSON) + (eloProbHome * 100.0 * HYBRID_WEIGHT_ELO);
        double rawAway = (poissonResult.getAwayWinProbability() * HYBRID_WEIGHT_POISSON) + (eloProbAway * 100.0 * HYBRID_WEIGHT_ELO);

        // 9. Market Anchoring (Sagesse des foules pour stabiliser le modèle)
        MarketProbs market = calculateMarketImpliedProbs(match.getOdds1(), match.getOddsN(), match.getOdds2());
        if (market != null) {
            rawHome = (rawHome * 0.70) + (market.home * 30.0); // Ancrage 30% sur le marché
            rawAway = (rawAway * 0.70) + (market.away * 30.0);
        }
        double rawDraw = 100.0 - rawHome - rawAway;

        // 10. CALIBRATION FINALE (Scaling de Platt pour corriger les biais de sur-confiance)
        double finalProbHome = rawHome;
        double finalProbAway = rawAway;
        double finalProbDraw = rawDraw;

        if (league != null && league.getCalibrationA() != null && league.getCalibrationB() != null) {
            finalProbHome = calibrationService.calibrate(rawHome, league.getCalibrationA(), league.getCalibrationB());
            finalProbAway = calibrationService.calibrate(rawAway, league.getCalibrationA(), league.getCalibrationB());
            finalProbDraw = 100.0 - finalProbHome - finalProbAway;
        }

        // 11. Explicabilité : Génération des notes techniques
        match.setMyNotes(generateExplainabilityNotes(homeLambda, awayLambda, eloDiff, home, away));

        return PredictionResult.builder()
                .homeWinProbability(round(finalProbHome))
                .drawProbability(round(finalProbDraw))
                .awayWinProbability(round(finalProbAway))
                .predictedHomeGoals(round(homeLambda))
                .predictedAwayGoals(round(awayLambda))
                .homePowerScore(poissonResult.getHomePowerScore())
                .awayPowerScore(poissonResult.getAwayPowerScore())
                .over2_5_Prob(poissonResult.getOver2_5_Prob())
                .under2_5_Prob(poissonResult.getUnder2_5_Prob())
                .bttsProb(poissonResult.getBttsProb())
                .doubleChance1N(round(finalProbHome + finalProbDraw))
                .doubleChanceN2(round(finalProbDraw + finalProbAway))
                .doubleChance12(round(finalProbHome + finalProbAway))
                .kellyStakeHome(calculateKelly(finalProbHome / 100.0, match.getOdds1()))
                .kellyStakeDraw(calculateKelly(finalProbDraw / 100.0, match.getOddsN()))
                .kellyStakeAway(calculateKelly(finalProbAway / 100.0, match.getOdds2()))
                .valueBetDetected(poissonResult.getValueBetDetected())
                .homeScoreOver0_5(poissonResult.getHomeScoreOver0_5())
                .homeScoreOver1_5(poissonResult.getHomeScoreOver1_5())
                .awayScoreOver0_5(poissonResult.getAwayScoreOver0_5())
                .awayScoreOver1_5(poissonResult.getAwayScoreOver1_5())
                .kellyOver2_5(poissonResult.getKellyOver2_5())
                .kellyBTTS(poissonResult.getKellyBTTS())
                .exactScore(poissonResult.getExactScore())
                .exactScoreProb(poissonResult.getExactScoreProb())
                .probUnder1_5(poissonResult.getProbUnder1_5())
                .probOver3_5(poissonResult.getProbOver3_5())
                .probBTTS_No(poissonResult.getProbBTTS_No())
                .build();
    }

    /**
     * Calcule les probabilités implicites réelles du marché en retirant la marge du bookmaker.
     */
    private MarketProbs calculateMarketImpliedProbs(Double o1, Double oN, Double o2) {
        if (o1 == null || oN == null || o2 == null) return null;
        double rawSum = (1.0 / o1) + (1.0 / oN) + (1.0 / o2);
        return new MarketProbs((1.0 / o1) / rawSum, (1.0 / oN) / rawSum, (1.0 / o2) / rawSum);
    }

    private record MarketProbs(double home, double draw, double away) {}

    /**
     * Génère un résumé technique expliquant le raisonnement de l'algorithme.
     * Les lambdas (hl, al) sont maintenant utilisés pour afficher les buts attendus finaux.
     */
    private String generateExplainabilityNotes(double hl, double al, double eloDiff, Team h, Team a) {
        StringBuilder sb = new StringBuilder("--- AUTO-ANALYSIS ---\n");

        // CORRECTION : Utilisation de hl et al pour montrer les espérances de buts finales
        sb.append(String.format("• Espérance de buts finale : %.2f (Dom) - %.2f (Ext)\n", hl, al));

        sb.append(String.format("• Forces Dixon-Coles intrinsèques (A/D) : %.2f / %.2f\n", h.getAttackStrength(), a.getDefenseStrength()));
        sb.append(String.format("• Écart Elo : %+.0f pts\n", eloDiff));

        if (h.getCurrentStats() != null) {
            if (h.getCurrentStats().getPpda() != null && h.getCurrentStats().getPpda() < 10.0) {
                sb.append("• Bonus Pressing appliqué (PPDA < 10)\n");
            }
            if (h.getCurrentStats().getFieldTilt() != null && h.getCurrentStats().getFieldTilt() > 55.0) {
                sb.append("• Domination territoriale forte détectée (Field Tilt > 55%)\n");
            }
        }

        return sb.toString();
    }

    /**
     * Version Professionnelle de la simulation de match.
     * Utilise la correction Dixon-Coles (Rho) et ajuste les probabilités selon la volatilité (Chaos).
     */
    private PredictionResult simulateMatchPro(double lambdaHome, double lambdaAway, MatchAnalysis match,
            TeamPerformance homePerf, TeamPerformance awayPerf, double rho) {

        double probHome = 0.0, probDraw = 0.0, probAway = 0.0;
        double probOver25 = 0.0, probBTTS = 0.0, probBTTS_No = 0.0;
        double probUnder1_5 = 0.0, probUnder2_5 = 0.0, probOver3_5 = 0.0;
        double probHomeOver05 = 0.0, probHomeOver15 = 0.0;
        double probAwayOver05 = 0.0, probAwayOver15 = 0.0;

        double maxProb = -1.0;
        String exactScore = "0-0";

        // --- UTILISATION DES PARAMÈTRES (Correction des Warnings) ---
        // Le "Game Chaos" mesure l'instabilité des deux équipes sur les 10 derniers matchs
        double gameChaos = (homePerf.volatility() + awayPerf.volatility()) / 2.0;

        for (int h = 0; h <= 9; h++) {
            for (int a = 0; a <= 9; a++) {
                // 1. Calcul de base Dixon-Coles
                double p = advancedPrediction.calculateProbability(h, a, lambdaHome, lambdaAway, rho);

                // 2. Ajustement par le Chaos (Volatilité)
                // Si le match est chaotique (> 1.2), on réduit légèrement la probabilité de match nul
                // car les scores ont tendance à diverger (plus de buts inattendus).
                if (h == a && gameChaos > 1.2) {
                    p *= 0.95;
                }

                // 3. Accumulation des probabilités
                if (h > a) probHome += p;
                else if (h == a) probDraw += p;
                else probAway += p;

                int totalGoals = h + a;
                if (totalGoals > 2.5) probOver25 += p;
                if (totalGoals < 2.5) probUnder2_5 += p;
                if (totalGoals < 1.5) probUnder1_5 += p;
                if (totalGoals > 3.5) probOver3_5 += p;

                if (h > 0 && a > 0) probBTTS += p; else probBTTS_No += p;
                if (h > 0) probHomeOver05 += p; if (h > 1) probHomeOver15 += p;
                if (a > 0) probAwayOver05 += p; if (a > 1) probAwayOver15 += p;

                if (p > maxProb) {
                    maxProb = p;
                    exactScore = h + "-" + a;
                }
            }
        }

        double total = probHome + probDraw + probAway;
        if (total == 0) total = 1.0;

        // Calcul de la valeur (Kelly)
        double kHome = calculateKelly(probHome / total, match.getOdds1());
        double kDraw = calculateKelly(probDraw / total, match.getOddsN());
        double kAway = calculateKelly(probAway / total, match.getOdds2());

        return PredictionResult.builder()
                .homeWinProbability(round((probHome / total) * 100))
                .drawProbability(round((probDraw / total) * 100))
                .awayWinProbability(round((probAway / total) * 100))
                .predictedHomeGoals(round(lambdaHome))
                .predictedAwayGoals(round(lambdaAway))
                .over2_5_Prob(round((probOver25 / total) * 100))
                .under2_5_Prob(round((probUnder2_5 / total) * 100))
                .bttsProb(round((probBTTS / total) * 100))
                .probBTTS_No(round((probBTTS_No / total) * 100))
                .doubleChance1N(round(((probHome + probDraw) / total) * 100))
                .doubleChanceN2(round(((probDraw + probAway) / total) * 100))
                .doubleChance12(round(((probHome + probAway) / total) * 100))
                .kellyStakeHome(kHome)
                .kellyStakeDraw(kDraw)
                .kellyStakeAway(kAway)
                .valueBetDetected(kHome > 0.05 ? "HOME" : (kAway > 0.05 ? "AWAY" : (kDraw > 0.05 ? "DRAW" : null)))
                .homeScoreOver0_5(round((probHomeOver05 / total) * 100))
                .homeScoreOver1_5(round((probHomeOver15 / total) * 100))
                .awayScoreOver0_5(round((probAwayOver05 / total) * 100))
                .awayScoreOver1_5(round((probAwayOver15 / total) * 100))
                .exactScore(exactScore)
                .exactScoreProb(round((maxProb / total) * 100))
                .probUnder1_5(round((probUnder1_5 / total) * 100))
                .probOver3_5(round((probOver3_5 / total) * 100))
                // Utilisation des ratings pour des Power Scores plus parlants
                .homePowerScore(round(homePerf.attackRating() * 10))
                .awayPowerScore(round(awayPerf.attackRating() * 10))
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

        // Calcul des jours de repos par rapport au dernier match connu
        if (!history.isEmpty()) {
            long restDays = Math.abs(Duration.between(history.getFirst().getMatchDate(), targetDate).toDays());
            // Malus si moins de 4 jours de repos (Cadence européenne)
            if (restDays < 4) {
                // On réduit légèrement l'attackRating et on augmente la volatilité
                attackRating *= 0.95;
                volatility *= 1.10;
            }
        }

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

    /**
     * Applique les métriques tactiques avancées (PPDA, Field Tilt).
     * On utilise la vulnérabilité de l'adversaire (oppStats + volatility) pour pondérer le bonus.
     */
    private double applyTacticalOverlay(double lambda, Team team, Team opponent, double opponentVolatility) {
        double factor = 1.0;
        TeamStats stats = team.getCurrentStats();
        TeamStats oppStats = opponent.getCurrentStats();

        if (stats != null && oppStats != null) {
            // 1. FIELD TILT vs DEFENSIVE RANK
            // Si je domine territorialement (> 55%) et que l'adversaire encaisse beaucoup de buts
            if (stats.getFieldTilt() != null && stats.getFieldTilt() > 55.0) {
                double vulnerabilityBonus = (oppStats.getGoalsAgainst() != null && oppStats.getGoalsAgainst() > 1.5) ? 0.08 : 0.04;
                factor += vulnerabilityBonus;
            }

            // 2. PPDA (Pressing) vs OPPONENT VOLATILITY
            // Un pressing intense (PPDA < 10) est dévastateur contre une équipe instable (volatility > 1.2)
            if (stats.getPpda() != null && stats.getPpda() < 10.0) {
                double pressingEffect = (opponentVolatility > 1.2) ? 0.07 : 0.03;
                factor += pressingEffect;
            }

            // 3. EXPLOITATION DES FAIBLESSES (Utilisation de oppStats)
            // Si l'adversaire a un PPDA très élevé (> 15), il ne presse pas.
            // Si ma force d'attaque est élevée, je vais avoir plus de temps pour construire.
            if (oppStats.getPpda() != null && oppStats.getPpda() > 15.0 && team.getAttackStrength() > 1.1) {
                factor += 0.05;
            }
        }
        return lambda * factor;
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

    // Dans PredictionEngineService.java
    private double applyContextualFactors(double lambda, boolean isHome, MatchAnalysis m) {
        double factor = 1.0;

        // Utilisation de l'impact score (ex: 0.8 pour un joueur majeur absent)
        double impactScore = isHome ? m.getHomeMissingImpactScore() : m.getAwayMissingImpactScore();

        if (impactScore > 0) {
            // Un impact de 1.0 (ex: Mbappé absent) réduit l'espérance de buts de 25%
            factor -= (impactScore * 0.25);
        }

        // Autres facteurs contextuels simplifiés
        if (isHome && m.isHomeTired()) factor -= 0.05;
        if (!isHome && m.isAwayNewCoach()) factor += 0.05;

        return lambda * factor;
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
