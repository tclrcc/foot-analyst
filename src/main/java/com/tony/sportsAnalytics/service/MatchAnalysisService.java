package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.*;
import com.tony.sportsAnalytics.model.dto.MatchAnalysisRequest;
import com.tony.sportsAnalytics.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchAnalysisService {

    private final MatchAnalysisRepository matchRepository;
    private final TeamRepository teamRepository;
    private final MatchAnalysisRepository analysisRepository;
    private final PredictionEngineService predictionEngine;

    @Transactional
    public MatchAnalysis analyzeAndSave(MatchAnalysisRequest request) {
        // 1. Récupération propre par ID (Fast Fail si l'équipe n'existe pas)
        Team homeTeam = teamRepository.findById(request.getHomeTeamId())
                .orElseThrow(() -> new EntityNotFoundException("Équipe domicile introuvable"));

        Team awayTeam = teamRepository.findById(request.getAwayTeamId())
                .orElseThrow(() -> new EntityNotFoundException("Équipe extérieur introuvable"));

        MatchAnalysis match = new MatchAnalysis();
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setMatchDate(request.getMatchDate());

        // Mapping Stats
        match.setHomeStats(request.getHomeStats());
        match.setAwayStats(request.getAwayStats());

        // Mapping Scores (Historique)
        match.setHomeScore(request.getHomeScore());
        match.setAwayScore(request.getAwayScore());

        // --- NOUVEAU : Mapping Cotes & Contexte ---
        match.setOdds1(request.getOdds1());
        match.setOddsN(request.getOddsN());
        match.setOdds2(request.getOdds2());

        if (request.getContext() != null) {
            match.setHomeKeyPlayerMissing(request.getContext().isHomeKeyPlayerMissing());
            match.setAwayKeyPlayerMissing(request.getContext().isAwayKeyPlayerMissing());
            match.setHomeTired(request.getContext().isHomeTired());
            match.setAwayNewCoach(request.getContext().isAwayNewCoach());
        }
        // ------------------------------------------

        // Calcul Prédictif
        List<MatchAnalysis> historiqueH2H = analysisRepository.findH2H(homeTeam, awayTeam, null);

        // Le moteur utilise maintenant l'objet 'match' qui contient le contexte
        PredictionResult prediction = predictionEngine.calculateMatchPrediction(match, historiqueH2H);
        match.setPrediction(prediction);

        return matchRepository.save(match);
    }
}
