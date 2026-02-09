package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.*;
import com.tony.sportsAnalytics.model.dto.MatchAnalysisRequest;
import com.tony.sportsAnalytics.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchAnalysisService {

    private final MatchAnalysisRepository matchAnalysisRepository;
    private final TeamRepository teamRepository;
    private final EloService eloService;
    private final TeamStatsService teamStatsService;
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

        // --- V13 : GESTION SAISON ---
        if (request.getSeason() != null && !request.getSeason().isEmpty()) {
            match.setSeason(request.getSeason());
        } else {
            // Déduction automatique si non fourni (ex: aout 2025 -> saison 2025-2026)
            match.setSeason(calculateSeason(request.getMatchDate()));
        }

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
        List<MatchAnalysis> h2h = matchAnalysisRepository.findH2H(homeTeam, awayTeam, null);

        // --- V12 : RÉCUPÉRATION DE LA FORME RÉCENTE (LAST 10) ---
        // On utilise la méthode repository existante (ou on crée une variante avec Pageable pour limiter à 10)
        // Pour simplifier, assumons qu'on prend tout et qu'on stream limit, ou mieux, on crée une requête repository.
        List<MatchAnalysis> homeLast10 = matchAnalysisRepository.findLastMatchesByTeam(homeTeam.getId(), PageRequest.of(0,10));
        List<MatchAnalysis> awayLast10 = matchAnalysisRepository.findLastMatchesByTeam(awayTeam.getId(), PageRequest.of(0,10));

        // Le moteur utilise maintenant l'objet 'match' qui contient le contexte
        PredictionResult prediction = predictionEngine.calculateMatchPrediction(
                match, h2h, homeLast10, awayLast10, leagueAvg
        );
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

        MatchAnalysis savedMatch = matchAnalysisRepository.save(match);

        // --- AJOUT V12 : RECALCUL AUTOMATIQUE DES STATS ---
        if (savedMatch.getHomeScore() != null) {
            teamStatsService.recalculateTeamStats(savedMatch.getHomeTeam().getId());
            teamStatsService.recalculateTeamStats(savedMatch.getAwayTeam().getId());
        }

        return savedMatch;
    }

    private String calculateSeason(LocalDateTime date) {
        int year = date.getYear();
        // Si on est en juillet ou après, c'est le début d'une nouvelle saison (ex: 2025-2026)
        // Sinon (janvier-juin), on est dans la fin de la saison précédente (ex: 2024-2025)
        if (date.getMonthValue() >= 7) {
            return year + "-" + (year + 1);
        } else {
            return (year - 1) + "-" + year;
        }
    }

    // V11 : Lister les matchs d'une équipe
    public List<MatchAnalysis> getMatchesByTeam(Long teamId) {
        return matchAnalysisRepository.findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(teamId, teamId);
    }

    // V11 : Mettre à jour un match existant
    @Transactional
    public MatchAnalysis updateMatch(Long matchId, MatchAnalysisRequest request) {
        MatchAnalysis match = matchAnalysisRepository.findById(matchId)
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

        MatchAnalysis savedMatch = matchAnalysisRepository.save(match);

        // --- AJOUT V12 : RECALCUL AUTOMATIQUE DES STATS ---
        teamStatsService.recalculateTeamStats(savedMatch.getHomeTeam().getId());
        teamStatsService.recalculateTeamStats(savedMatch.getAwayTeam().getId());

        return savedMatch;
    }
}
