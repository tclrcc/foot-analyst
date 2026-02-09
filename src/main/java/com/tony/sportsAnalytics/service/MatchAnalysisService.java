package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.*;
import com.tony.sportsAnalytics.model.dto.MatchAnalysisRequest;
import com.tony.sportsAnalytics.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchAnalysisService {

    private final MatchAnalysisRepository matchRepository;
    private final TeamRepository teamRepository;
    private final PredictionEngineService predictionEngine;

    @Transactional
    public MatchAnalysis analyzeAndSave(MatchAnalysisRequest request) {
        // 1. Récupération propre par ID (Fast Fail si l'équipe n'existe pas)
        Team homeTeam = teamRepository.findById(request.getHomeTeamId())
                .orElseThrow(() -> new EntityNotFoundException("Équipe domicile introuvable"));

        Team awayTeam = teamRepository.findById(request.getAwayTeamId())
                .orElseThrow(() -> new EntityNotFoundException("Équipe extérieur introuvable"));

        // 2. Mapping DTO -> Entity
        MatchAnalysis match = new MatchAnalysis();
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setMatchDate(request.getMatchDate());
        match.setHomeStats(request.getHomeStats());
        match.setAwayStats(request.getAwayStats());
        match.setHomeScore(request.getHomeScore());
        match.setAwayScore(request.getAwayScore());

        // 3. Calcul (seulement si ce n'est pas juste une saisie d'historique pur)
        // On calcule toujours la prédiction pour voir ce que l'algo AURAIT dit
        PredictionResult prediction = predictionEngine.calculateMatchPrediction(match);
        match.setPrediction(prediction);

        return matchRepository.save(match);
    }
}
