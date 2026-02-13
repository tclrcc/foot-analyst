package com.tony.sportsAnalytics.controller;

import com.tony.sportsAnalytics.job.DailyUpdateJob;
import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.repository.LeagueRepository;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import com.tony.sportsAnalytics.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    private final DailyUpdateJob dailyUpdateJob;
    private final DataImportService importService;
    private final BacktestingService backtestingService;
    private final MatchAnalysisService matchAnalysisService;
    private final ParameterEstimationService parameterEstimationService;
    private final AnalysisOrchestrator orchestrator;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final TeamStatsService teamStatsService;
    private final RankingService rankingService;

    // 1. Récupérer la liste des codes dispos (PL, L1...) pour le dropdown
    @GetMapping("/leagues-codes")
    public ResponseEntity<Set<String>> getAvailableLeagueCodes() {
        return ResponseEntity.ok(importService.getAvailableLeagues());
    }

    // 2. Import Unitaire
    @PostMapping("/import/{leagueCode}")
    public ResponseEntity<String> importSpecificLeague(
            @PathVariable String leagueCode,
            @RequestParam(defaultValue = "false") boolean forceUpdate) {

        String report = importService.importLeagueData(leagueCode, forceUpdate);

        // ✅ On recalcule une seule fois à la fin de l'import spécifique
        orchestrator.refreshUpcomingPredictions();

        return ResponseEntity.ok(report + "\nPrédictions mises à jour.");
    }

    @PostMapping("/import/all")
    public ResponseEntity<String> importAllLeagues(
            @RequestParam(defaultValue = "false") boolean forceUpdate) {

        String report = importService.importAllLeagues(forceUpdate);

        // ✅ On recalcule UNE SEULE FOIS à la fin de l'import des 5 ligues (Énorme gain de perf !)
        orchestrator.refreshUpcomingPredictions();

        return ResponseEntity.ok(report + "\nPrédictions mises à jour.");
    }

    /**
     * Endpoint pour lancer le backtesting sur une période donnée.
     * Exemple : POST /api/admin/backtest?from=2025-08-01&to=2026-02-01
     */
    @PostMapping("/backtest")
    public ResponseEntity<String> runBacktest(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {

        log.info("🚀 Lancement du backtest du {} au {}", from, to);
        backtestingService.runBacktest(from, to);

        return ResponseEntity.ok("Backtest lancé ! Regarde tes logs pour le Brier Score final.");
    }

    @PostMapping("/recalculate-upcoming")
    public ResponseEntity<String> recalculateAllUpcoming() {
        log.info("🔄 Relance massive des prédictions pour les matchs à venir");
        int count = matchAnalysisService.recalculateAllUpcoming();
        return ResponseEntity.ok(count + " analyses mises à jour avec les nouveaux paramètres.");
    }

    @PostMapping("/estimate/{leagueId}")
    public ResponseEntity<String> estimateParams(@PathVariable String leagueId) {
        log.info("🧮 Lancement de l'estimation des forces (Alpha/Beta) pour la ligue {}", leagueId);
        // Appel à la nouvelle méthode créée
        parameterEstimationService.runEstimationForLeague(leagueId);
        return ResponseEntity.ok("Estimation des paramètres lancée ! Vérifiez les logs.");
    }

    /**
     * Endpoint pour forcer l'exécution des 3 jobs quotidiens manuellement.
     * Très utile pour l'environnement local ou le rattrapage de données.
     */
    @PostMapping("/force-daily-jobs")
    public ResponseEntity<String> forceDailyJobs() {
        log.info("🚀 Lancement manuel des jobs quotidiens demandé par l'admin");
        try {
            // On appelle les 3 méthodes dans l'ordre logique d'exécution métier
            dailyUpdateJob.updateResults();
            dailyUpdateJob.updateFixtures();
            dailyUpdateJob.recalibrateModel();

            return ResponseEntity.ok("✅ Les 3 jobs (Résultats, Fixtures, Recalibrage) ont été exécutés avec succès ! Vérifiez les logs pour les détails.");
        } catch (Exception e) {
            log.error("❌ Erreur critique lors de l'exécution manuelle des jobs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur technique lors de l'exécution : " + e.getMessage());
        }
    }

    @PostMapping("/recalculate-all")
    public ResponseEntity<?> recalculateAllDatabase() {
        // 1. Recalculer toutes les statistiques individuelles (Dom/Ext inclus)
        List<Team> allTeams = teamRepository.findAll();
        for (Team team : allTeams) {
            teamStatsService.recalculateTeamStats(team.getId());
        }

        // 2. Mettre à jour tous les classements (Général, Dom, Ext)
        List<League> allLeagues = leagueRepository.findAll();
        for (League league : allLeagues) {
            rankingService.updateLeagueRankings(league.getId());
        }

        return ResponseEntity.ok(java.util.Map.of("message", "Base de données entièrement recalculée avec succès !"));
    }
}
