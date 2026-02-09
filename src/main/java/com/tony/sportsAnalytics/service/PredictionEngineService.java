package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.model.TeamStats;
import org.springframework.stereotype.Service;

@Service
public class PredictionEngineService {

    // --- CONFIGURATION DES POIDS (L'alchimie de l'algo) ---

    // Avantage domicile (ex: équivaut à un bonus de points fictifs)
    private static final double HOME_ADVANTAGE_WEIGHT = 15.0;

    // Importance du classement (Inverse : meilleur classement = plus de points)
    private static final double RANK_WEIGHT = 2.0;

    // Importance de la forme récente (Points sur 5 matchs)
    // C'est souvent plus prédictif que le classement général
    private static final double FORM_WEIGHT = 5.0;

    // Importance des xG (Expected Goals) - Très prédictif sur la qualité de jeu
    private static final double XG_WEIGHT = 10.0;

    /**
     * Calcule les probabilités pour un match donné.
     */
    public PredictionResult calculateMatchPrediction(MatchAnalysis match) {

        // 1. Calculer le "Power Score" de chaque équipe
        double homeScore = calculateTeamPowerScore(match.getHomeStats(), true);
        double awayScore = calculateTeamPowerScore(match.getAwayStats(), false);

        // 2. Calculer l'écart total
        double totalScore = homeScore + awayScore;

        // 3. Convertir en pourcentages (Normalisation)
        // Note : C'est une simplification. Pour un vrai modèle de foot,
        // le match nul est complexe à modéliser sans Loi de Poisson.
        // Ici, on va déduire une "marge d'incertitude" pour le nul.

        double rawHomeProb = (homeScore / totalScore) * 100;
        double rawAwayProb = (awayScore / totalScore) * 100;

        // On arbitre que 25% des matchs finissent en nul (moyenne standard)
        // On redistribue les % restants
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
        double score = 100.0; // Score de base pour éviter les divisions par zéro

        // Bonus Domicile
        if (isHome) {
            score += HOME_ADVANTAGE_WEIGHT;
        }

        // Points par match (Qualité globale)
        // On suppose que stats.getPoints() est le total.
        // Idéalement il faudrait diviser par le nombre de matchs joués.
        score += stats.getPoints() * 1.5;

        // Forme récente (Qualité actuelle)
        if (stats.getLast5MatchesPoints() != null) {
            score += stats.getLast5MatchesPoints() * FORM_WEIGHT;
        }

        // xG (Qualité offensive créative)
        if (stats.getXG() != null) {
            score += stats.getXG() * XG_WEIGHT;
        }

        // Classement (Plus le rang est petit, mieux c'est)
        // On inverse : un 1er (20 équipes) prend plus de points qu'un 20ème.
        // Formule arbitraire : (21 - Rank) * Poids
        score += (21 - stats.getRank()) * RANK_WEIGHT;

        return score;
    }

    // Helper pour arrondir à 2 décimales
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
