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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
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
        List<MatchAnalysis> h2h = matchAnalysisRepository.findH2H(homeTeam, awayTeam, match.getMatchDate());
        List<MatchAnalysis> homeHistory = matchAnalysisRepository.findLastMatchesByTeam(homeTeam.getId(), match.getMatchDate());
        List<MatchAnalysis> awayHistory = matchAnalysisRepository.findLastMatchesByTeam(awayTeam.getId(), match.getMatchDate());

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


    private TeamStats mapToTeamStats(MatchAnalysisRequest.TeamStatsRequest dto) {
        if (dto == null) {
            log.warn("⚠️ ALERTE : Le DTO TeamStatsRequest reçu est NULL !");
            return new TeamStats();
        }

        log.info("🗺️ MAPPING DTO -> ENTITY : Valeurs reçues -> AvgShots: {}, AvgPossession: {}", dto.getAvgShots(), dto.getAvgPossession());

        TeamStats stats = new TeamStats();
        stats.setRank(dto.getRank());
        stats.setPoints(dto.getPoints());
        stats.setXG(dto.getXG());
        stats.setXGA(dto.getXGA()); // Ajouté
        stats.setGoalsFor(dto.getGoalsFor());
        stats.setGoalsAgainst(dto.getGoalsAgainst());
        stats.setLast5MatchesPoints(dto.getLast5MatchesPoints());
        stats.setGoalsForLast5(dto.getGoalsForLast5());
        stats.setGoalsAgainstLast5(dto.getGoalsAgainstLast5());
        stats.setMatchesPlayed(dto.getMatchesPlayed());
        stats.setMatchesPlayedHome(dto.getMatchesPlayedHome());
        stats.setMatchesPlayedAway(dto.getMatchesPlayedAway());
        stats.setVenuePoints(dto.getVenuePoints());

        // --- MAPPING DES NOUVELLES STATS ---
        stats.setAvgShots(dto.getAvgShots());
        stats.setAvgShotsOnTarget(dto.getAvgShotsOnTarget());
        stats.setAvgPossession(dto.getAvgPossession());
        stats.setAvgCorners(dto.getAvgCorners());
        stats.setAvgCrosses(dto.getAvgCrosses());
        stats.setPpda(dto.getPpda());
        stats.setFieldTilt(dto.getFieldTilt());
        stats.setDeepEntries(dto.getDeepEntries());

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

    @Transactional
    public MatchAnalysis recalculatePrediction(Long matchId) {
        MatchAnalysis match = matchAnalysisRepository.findById(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match introuvable ID: " + matchId));

        Team homeTeam = match.getHomeTeam();
        Team awayTeam = match.getAwayTeam();
        LocalDateTime limitDate = match.getMatchDate();

        // --- 🚨 NOUVEAUTÉ CRUCIALE : MISE À JOUR DES STATS EMBARQUÉES ---
        // Avant de recalculer la prédiction, on force la mise à jour de l'objet TeamStats du match
        // Cela va remplir les fameuses colonnes "avg_shots", "avg_possession", etc. en base de données !
        log.info("🔄 Mise à jour des statistiques embarquées pour le match {}", matchId);
        match.setHomeStats(teamStatsService.getSuggestedStats(homeTeam.getId()));
        match.setAwayStats(teamStatsService.getSuggestedStats(awayTeam.getId()));

        // 1. Récupération des historiques à l'instant T
        List<MatchAnalysis> h2h = matchAnalysisRepository.findH2H(homeTeam, awayTeam, match.getMatchDate());
        List<MatchAnalysis> homeHistory = matchAnalysisRepository.findLastMatchesByTeam(homeTeam.getId(), limitDate);
        List<MatchAnalysis> awayHistory = matchAnalysisRepository.findLastMatchesByTeam(awayTeam.getId(), limitDate);

        // 2. Récupération de la moyenne de buts de la ligue
        double leagueAvg = (homeTeam.getLeague() != null && homeTeam.getLeague().getAverageGoalsPerMatch() != null)
                ? homeTeam.getLeague().getAverageGoalsPerMatch() : 2.5;

        // 3. Relance du moteur avec les nouveaux paramètres (Calibration, Poids, etc.)
        PredictionResult newPrediction = predictionEngine.calculateMatchPrediction(
                match, h2h, homeHistory, awayHistory, leagueAvg
        );

        match.setPrediction(newPrediction);

        // 4. Mise à jour des notes d'explicabilité si nécessaire
        return matchAnalysisRepository.save(match);
    }

    @Transactional
    public int recalculateAllUpcoming() {
        // Récupère tous les matchs à partir de maintenant
        LocalDateTime now = LocalDateTime.now();
        List<MatchAnalysis> upcoming = matchAnalysisRepository.findUpcomingMatches(now); //

        for (MatchAnalysis m : upcoming) {
            // Appelle votre logique de recalcul existante pour chaque match
            recalculatePrediction(m.getId());
        }
        return upcoming.size();
    }
}
