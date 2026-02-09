package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.config.PredictionProperties;
import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.model.TeamStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PredictionEngineService {

    private final PredictionProperties props;

    public PredictionResult calculateMatchPrediction(MatchAnalysis match) {
        double homeScore = calculateTeamPowerScore(match.getHomeStats(), true);
        double awayScore = calculateTeamPowerScore(match.getAwayStats(), false);

        double totalScore = homeScore + awayScore;

        double rawHomeProb = (homeScore / totalScore) * 100;
        double rawAwayProb = (awayScore / totalScore) * 100;

        // Modèle statique pour le nul (Pourrait aussi être dans la config !)
        double drawProb = 25.0;
        double adjustmentFactor = (100.0 - drawProb) / 100.0;

        return new PredictionResult(
                round(rawHomeProb * adjustmentFactor),
                drawProb,
                round(rawAwayProb * adjustmentFactor),
                round(homeScore),
                round(awayScore)
        );
    }

    private double calculateTeamPowerScore(TeamStats stats, boolean isHome) {
        double score = props.getBaseScore();

        // 1. Avantage Domicile
        if (stats.getVenuePoints() != null && stats.getVenueMatches() != null && stats.getVenueMatches() > 0) {
            double ppgInVenue = (double) stats.getVenuePoints() / stats.getVenueMatches();
            score += ppgInVenue * props.getVenueImportance();
        } else {
            // Fallback si pas de données (ex: début de saison)
            // On donne un petit avantage par défaut au domicile (2.0 pts/m * poids / 2)
            if (isHome) score += 5.0;
        }

        // 2. Points (Réalité comptable)
        if (stats.getPoints() != null) {
            score += stats.getPoints() * props.getPointsImportance();
        }

        // 3. Forme (Dynamique actuelle)
        if (stats.getLast5MatchesPoints() != null) {
            score += stats.getLast5MatchesPoints() * props.getFormImportance();
        }

        // 4. xG (Qualité de la création d'occasions)
        if (stats.getXG() != null) {
            score += stats.getXG() * props.getXgImportance();
        }

        // 5. Classement (Hiérarchie)
        if (stats.getRank() != null) {
            score += (21 - stats.getRank()) * props.getRankImportance();
        }

        // 6. NOUVEAU : Différence de Buts (Force réelle attaque/défense)
        if (stats.getGoalsFor() != null && stats.getGoalsAgainst() != null) {
            int goalDiff = stats.getGoalsFor() - stats.getGoalsAgainst();
            // On ajoute (ou retire) des points basés sur la différence de buts
            score += goalDiff * props.getGoalDiffImportance();
        }

        return score;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
