package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.PredictionResult;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.model.TeamStats;
import com.tony.sportsAnalytics.model.dto.MatchAnalysisRequest;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchAnalysisService {

    private final MatchAnalysisRepository matchAnalysisRepository;
    private final TeamRepository teamRepository;
    private final PredictionEngineService predictionEngine;
    private final EloService eloService;
    private final TeamStatsService teamStatsService;

    @Transactional
    public MatchAnalysis analyzeAndSave(MatchAnalysisRequest request) {
        // 1. Récupération propre par ID
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
            match.setSeason(calculateSeason(request.getMatchDate()));
        }

        // --- MAPPING STATS (CORRECTION ERREUR TYPE) ---
        // On convertit le DTO (Request) vers l'Entité (TeamStats) via une méthode helper
        match.setHomeStats(mapToTeamStats(request.getHomeStats()));
        match.setAwayStats(mapToTeamStats(request.getAwayStats()));
        // ----------------------------------------------

        // Mapping Scores (Historique)
        match.setHomeScore(request.getHomeScore());
        match.setAwayScore(request.getAwayScore());

        // Stats détaillées du match (Tirs, Possession...)
        if (request.getHomeMatchStats() != null) {
            match.setHomeMatchStats(request.getHomeMatchStats());
        }
        if (request.getAwayMatchStats() != null) {
            match.setAwayMatchStats(request.getAwayMatchStats());
        }

        // --- MAPPING COTES & CONTEXTE ---
        match.setOdds1(request.getOdds1());
        match.setOddsN(request.getOddsN());
        match.setOdds2(request.getOdds2());
        match.setOddsOver15(request.getOddsOver15());
        match.setOddsOver25(request.getOddsOver25());
        match.setOddsBTTSYes(request.getOddsBTTSYes());

        if (request.getContext() != null) {
            match.setHomeKeyPlayerMissing(request.getContext().isHomeKeyPlayerMissing());
            match.setAwayKeyPlayerMissing(request.getContext().isAwayKeyPlayerMissing());
            match.setHomeTired(request.getContext().isHomeTired());
            match.setAwayNewCoach(request.getContext().isAwayNewCoach());
        }

        // On récupère la moyenne de buts du championnat
        double leagueAvg = homeTeam.getLeague().getAverageGoalsPerTeam();

        // Calcul Prédictif
        List<MatchAnalysis> h2h = matchAnalysisRepository.findH2H(homeTeam, awayTeam, null);
        List<MatchAnalysis> homeHistory = matchAnalysisRepository.findLastMatchesByTeam(homeTeam.getId());
        List<MatchAnalysis> awayHistory = matchAnalysisRepository.findLastMatchesByTeam(awayTeam.getId());

        PredictionResult prediction = predictionEngine.calculateMatchPrediction(
                match, h2h, homeHistory, awayHistory, leagueAvg
        );
        match.setPrediction(prediction);

        // --- APPRENTISSAGE ELO ---
        if (request.getHomeScore() != null && request.getAwayScore() != null) {
            eloService.updateRatings(homeTeam, awayTeam, request.getHomeScore(), request.getAwayScore());
            teamRepository.save(homeTeam);
            teamRepository.save(awayTeam);
        }

        MatchAnalysis savedMatch = matchAnalysisRepository.save(match);

        // --- RECALCUL AUTOMATIQUE DES STATS ---
        if (savedMatch.getHomeScore() != null) {
            teamStatsService.recalculateTeamStats(savedMatch.getHomeTeam().getId());
            teamStatsService.recalculateTeamStats(savedMatch.getAwayTeam().getId());
        }

        return savedMatch;
    }

    // --- V11 : Update match ---
    @Transactional
    public MatchAnalysis updateMatch(Long matchId, MatchAnalysisRequest request) {
        MatchAnalysis match = matchAnalysisRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match introuvable ID: " + matchId));

        match.setMatchDate(request.getMatchDate());
        match.setHomeScore(request.getHomeScore());
        match.setAwayScore(request.getAwayScore());

        if (request.getHomeMatchStats() != null) match.setHomeMatchStats(request.getHomeMatchStats());
        if (request.getAwayMatchStats() != null) match.setAwayMatchStats(request.getAwayMatchStats());

        MatchAnalysis saved = matchAnalysisRepository.save(match);

        // Recalcul stats après update
        teamStatsService.recalculateTeamStats(saved.getHomeTeam().getId());
        teamStatsService.recalculateTeamStats(saved.getAwayTeam().getId());

        return saved;
    }

    // --- V11 : Get matchs d'une équipe ---
    public List<MatchAnalysis> getMatchesByTeam(Long teamId) {
        return matchAnalysisRepository.findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(teamId, teamId);
    }

    // --- HELPER : Mapping DTO -> Entity ---
    private TeamStats mapToTeamStats(MatchAnalysisRequest.TeamStatsRequest dto) {
        if (dto == null) return new TeamStats(); // Retourne un objet vide pour éviter le null

        TeamStats stats = new TeamStats();
        stats.setRank(dto.getRank());
        stats.setPoints(dto.getPoints());
        stats.setXG(dto.getXG());
        stats.setGoalsFor(dto.getGoalsFor());
        stats.setGoalsAgainst(dto.getGoalsAgainst());
        stats.setLast5MatchesPoints(dto.getLast5MatchesPoints());
        stats.setGoalsForLast5(dto.getGoalsForLast5());
        stats.setGoalsAgainstLast5(dto.getGoalsAgainstLast5());
        stats.setMatchesPlayed(dto.getMatchesPlayed());
        stats.setMatchesPlayedHome(dto.getMatchesPlayedHome());
        stats.setMatchesPlayedAway(dto.getMatchesPlayedAway());
        stats.setVenuePoints(dto.getVenuePoints());
        return stats;
    }

    // --- HELPER : Saison ---
    private String calculateSeason(LocalDateTime date) {
        int year = date.getYear();
        if (date.getMonthValue() >= 7) {
            return year + "-" + (year + 1);
        } else {
            return (year - 1) + "-" + year;
        }
    }
}
