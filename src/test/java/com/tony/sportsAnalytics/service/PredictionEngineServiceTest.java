package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.model.TeamStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionEngineServiceTest {

    private PredictionEngineService predictionEngine;

    @BeforeEach
    void setUp() {
        predictionEngine = new PredictionEngineService(null);
    }

    @Test
    @DisplayName("Devrait donner l'avantage à l'équipe à domicile à stats égales")
    void shouldFavorHomeTeamWhenStatsAreEqual() {
        TeamStats equalStatsHome = createStats(10, 30, 1.5, 10);
        TeamStats equalStatsAway = createStats(10, 30, 1.5, 10);

        MatchAnalysis match = new MatchAnalysis();
        match.setHomeStats(equalStatsHome);
        match.setAwayStats(equalStatsAway);

        PredictionResult result = predictionEngine.calculateMatchPrediction(match, null);

        assertThat(result.getHomePowerScore()).isGreaterThan(result.getAwayPowerScore());
        assertThat(result.getHomeWinProbability()).isGreaterThan(result.getAwayWinProbability());
    }

    @Test
    @DisplayName("Une équipe très forte doit avoir une probabilité élevée")
    void strongTeamShouldHaveHighProbability() {
        // ARRANGE
        // Equipe forte : 1er, 80 pts, 2.5 xG, super forme
        TeamStats strongHome = createStats(1, 80, 2.5, 15);
        // Equipe faible : 20ème, 10 pts, 0.5 xG, forme nulle
        TeamStats weakAway = createStats(20, 10, 0.5, 0);

        MatchAnalysis match = new MatchAnalysis();
        match.setHomeStats(strongHome);
        match.setAwayStats(weakAway);

        // ACT
        PredictionResult result = predictionEngine.calculateMatchPrediction(match, null);

        // ASSERT
        // Avec le nouveau calibrage, on attend > 60% (ce qui est énorme avec 25% de nul forcé)
        System.out.println("Home Win Prob: " + result.getHomeWinProbability()); // Pour debug
        assertThat(result.getHomeWinProbability()).isGreaterThan(60.0);
        assertThat(result.getAwayWinProbability()).isLessThan(20.0);
    }

    @Test
    @DisplayName("Devrait gérer les valeurs nulles")
    void shouldHandleNullOptionalValues() {
        TeamStats incompleteStats = new TeamStats(10, 30, 20, 20, null, null, null, null, null, null);
        MatchAnalysis match = new MatchAnalysis();
        match.setHomeStats(incompleteStats);
        match.setAwayStats(incompleteStats);

        PredictionResult result = predictionEngine.calculateMatchPrediction(match, null);
        assertThat(result).isNotNull();
    }

    private TeamStats createStats(Integer rank, Integer points, Double xG, Integer last5Points) {
        return new TeamStats(rank, points, 0, 0, last5Points, null, null, xG, null, null);
    }
}
