package com.tony.sportsAnalytics.controller;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.service.BacktestingService;
import com.tony.sportsAnalytics.service.DataImportService;
import com.tony.sportsAnalytics.service.MatchAnalysisService;
import com.tony.sportsAnalytics.service.ParameterEstimationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final MatchAnalysisRepository matchAnalysisRepository;
    private final DataImportService importService;
    private final BacktestingService backtestingService;
    private final MatchAnalysisService matchAnalysisService;
    private final ParameterEstimationService parameterEstimationService;

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
        return ResponseEntity.ok(importService.importLeagueData(leagueCode, forceUpdate));
    }

    // 3. Import Global
    @PostMapping("/import/all")
    public ResponseEntity<String> importAllLeagues(
            @RequestParam(defaultValue = "false") boolean forceUpdate) {
        return ResponseEntity.ok(importService.importAllLeagues(forceUpdate));
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
}
