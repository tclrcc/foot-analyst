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
}
