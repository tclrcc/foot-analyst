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
        double score = props.getBaseScore(); // Utilisation de la config

        if (isHome) score += props.getHomeAdvantage();
        if (stats.getPoints() != null) score += stats.getPoints() * props.getPointsImportance();
        if (stats.getLast5MatchesPoints() != null) score += stats.getLast5MatchesPoints() * props.getFormImportance();
        if (stats.getXG() != null) score += stats.getXG() * props.getXgImportance();
        if (stats.getRank() != null) score += (21 - stats.getRank()) * props.getRankImportance();

        return score;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
