package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class) // Active Mockito pour cette classe
class MatchAnalysisServiceTest {

    @Mock // Crée une fausse instance du repository
    private MatchAnalysisRepository repository;

    @Mock // Crée une fausse instance de l'engine
    private PredictionEngineService predictionEngine;

    @InjectMocks // Injecte les mocks dans le service à tester
    private MatchAnalysisService matchAnalysisService;

    @Test
    void analyzeAndSave_ShouldCalculatePredictionAndSave() {
        // ARRANGE
        MatchAnalysis inputMatch = new MatchAnalysis();
        inputMatch.setHomeTeamName("PSG");

        PredictionResult expectedPrediction = new PredictionResult(50.0, 25.0, 25.0, 100.0, 90.0);

        // On dit au Mock Engine : "Si on t'appelle, renvoie ce résultat prévu"
        when(predictionEngine.calculateMatchPrediction(any(MatchAnalysis.class)))
                .thenReturn(expectedPrediction);

        // On dit au Mock Repository : "Quand on sauvegarde, renvoie l'objet qu'on t'a donné"
        when(repository.save(any(MatchAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        MatchAnalysis savedMatch = matchAnalysisService.analyzeAndSave(inputMatch);

        // ASSERT
        // 1. Vérifier que la prédiction a été attachée à l'objet
        assertThat(savedMatch.getPrediction()).isEqualTo(expectedPrediction);

        // 2. Vérifier que les méthodes des dépendances ont bien été appelées une fois
        verify(predictionEngine, times(1)).calculateMatchPrediction(inputMatch);
        verify(repository, times(1)).save(inputMatch);
    }
}
