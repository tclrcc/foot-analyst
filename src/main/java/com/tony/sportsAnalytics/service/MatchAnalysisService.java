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
    private final EloService eloService;
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

        if (request.getHomeMatchStats() != null) {
            match.setHomeMatchStats(request.getHomeMatchStats());
        }
        if (request.getAwayMatchStats() != null) {
            match.setAwayMatchStats(request.getAwayMatchStats());
        }

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

        // On récupère la moyenne de buts du championnat de l'équipe à domicile
        double leagueAvg = homeTeam.getLeague().getAverageGoalsPerTeam();

        // Calcul Prédictif
        List<MatchAnalysis> historiqueH2H = analysisRepository.findH2H(homeTeam, awayTeam, null);

        // Le moteur utilise maintenant l'objet 'match' qui contient le contexte
        PredictionResult prediction = predictionEngine.calculateMatchPrediction(match, historiqueH2H, leagueAvg);
        match.setPrediction(prediction);

        // --- APPRENTISSAGE ELO (V8) ---
        // Si c'est un résultat réel (Score saisi dans Admin)
        if (request.getHomeScore() != null && request.getAwayScore() != null) {
            // 1. On met à jour l'ELO des équipes
            eloService.updateRatings(homeTeam, awayTeam, request.getHomeScore(), request.getAwayScore());

            // 2. On sauvegarde les équipes avec leur nouvel ELO
            teamRepository.save(homeTeam);
            teamRepository.save(awayTeam);

            // Log pour vérifier
            System.out.println("⚡ ELO Update: " + homeTeam.getName() + " (" + homeTeam.getEloRating() + ") vs " + awayTeam.getName() + " (" + awayTeam.getEloRating() + ")");
        }

        return matchRepository.save(match);
    }

    // ...

    // V11 : Lister les matchs d'une équipe
    public List<MatchAnalysis> getMatchesByTeam(Long teamId) {
        return analysisRepository.findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(teamId, teamId);
    }

    // V11 : Mettre à jour un match existant
    @Transactional
    public MatchAnalysis updateMatch(Long matchId, MatchAnalysisRequest request) {
        MatchAnalysis match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match introuvable ID: " + matchId));

        // Mise à jour des infos de base
        match.setMatchDate(request.getMatchDate());
        match.setHomeScore(request.getHomeScore());
        match.setAwayScore(request.getAwayScore());

        // Mise à jour des stats détaillées (V10)
        if (request.getHomeMatchStats() != null) match.setHomeMatchStats(request.getHomeMatchStats());
        if (request.getAwayMatchStats() != null) match.setAwayMatchStats(request.getAwayMatchStats());

        // Mise à jour du Contexte (Blessures...) - Si présent dans la request
        if (request.getContext() != null) {
            match.setHomeKeyPlayerMissing(request.getContext().isHomeKeyPlayerMissing());
            match.setAwayKeyPlayerMissing(request.getContext().isAwayKeyPlayerMissing());
            match.setHomeTired(request.getContext().isHomeTired());
            match.setAwayNewCoach(request.getContext().isAwayNewCoach());
        }

        // Note : On ne recalcule pas l'ELO ici pour éviter de casser l'historique complet.
        // On suppose que c'est une correction de données (stats, xG...).

        return matchRepository.save(match);
    }
}
