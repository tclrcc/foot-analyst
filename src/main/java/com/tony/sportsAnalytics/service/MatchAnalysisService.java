package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchAnalysisService {

    private final MatchAnalysisRepository repository;
    private final PredictionEngineService predictionEngine;

    @Transactional // Garantit que tout se passe bien ou rien ne se passe
    public MatchAnalysis analyzeAndSave(MatchAnalysis matchAnalysis) {

        // 1. Appel de l'algo de prédiction
        PredictionResult prediction = predictionEngine.calculateMatchPrediction(matchAnalysis);

        // 2. Enrichissement de l'objet
        matchAnalysis.setPrediction(prediction);

        // 3. Sauvegarde en base
        return repository.save(matchAnalysis);
    }

    // Méthode pour recalculer si tu modifies l'algo plus tard
    public void reprocessAllPredictions() {
        var allMatches = repository.findAll();
        allMatches.forEach(this::analyzeAndSave);
    }
}
