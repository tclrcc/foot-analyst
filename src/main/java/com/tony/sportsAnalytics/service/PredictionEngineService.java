package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.model.TeamStats;
import org.springframework.stereotype.Service;

@Service
public class PredictionEngineService {

    // --- CONFIGURATION DES POIDS (L'alchimie de l'algo) ---
    // On baisse la base pour augmenter l'impact des vraies stats
    private static final double BASE_SCORE = 50.0;

    // Avantage domicile (ex: équivaut à un bonus de points fictifs)
    private static final double HOME_ADVANTAGE_WEIGHT = 15.0;

    // Importance du classement (Inverse : meilleur classement = plus de points)
    private static final double RANK_WEIGHT = 3.0;

    // Les points reflètent la réalité de la saison
    private static final double POINTS_WEIGHT = 2.0;

    // Importance de la forme récente (Points sur 5 matchs)
    // C'est souvent plus prédictif que le classement général
    private static final double FORM_WEIGHT = 5.0;

    // Importance des xG (Expected Goals) - Très prédictif sur la qualité de jeu
    private static final double XG_WEIGHT = 10.0;

    /**
     * Calcule les probabilités pour un match donné.
     */
    public PredictionResult calculateMatchPrediction(MatchAnalysis match) {
        double homeScore = calculateTeamPowerScore(match.getHomeStats(), true);
        double awayScore = calculateTeamPowerScore(match.getAwayStats(), false);

        double totalScore = homeScore + awayScore;

        double rawHomeProb = (homeScore / totalScore) * 100;
        double rawAwayProb = (awayScore / totalScore) * 100;

        // Modèle statique pour le nul (Phase 1)
        double drawProb = 25.0;
        double adjustmentFactor = (100.0 - drawProb) / 100.0;

        double finalHomeProb = rawHomeProb * adjustmentFactor;
        double finalAwayProb = rawAwayProb * adjustmentFactor;

        return new PredictionResult(
                round(finalHomeProb),
                drawProb,
                round(finalAwayProb),
                round(homeScore),
                round(awayScore)
        );
    }

    private double calculateTeamPowerScore(TeamStats stats, boolean isHome) {
        double score = BASE_SCORE;

        if (isHome) {
            score += HOME_ADVANTAGE_WEIGHT;
        }

        if (stats.getPoints() != null) {
            score += stats.getPoints() * POINTS_WEIGHT;
        }

        if (stats.getLast5MatchesPoints() != null) {
            score += stats.getLast5MatchesPoints() * FORM_WEIGHT;
        }

        if (stats.getXG() != null) {
            score += stats.getXG() * XG_WEIGHT;
        }

        if (stats.getRank() != null) {
            score += (21 - stats.getRank()) * RANK_WEIGHT;
        }

        return score;
    }

    // Helper pour arrondir à 2 décimales
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
