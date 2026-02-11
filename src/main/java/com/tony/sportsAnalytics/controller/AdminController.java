package com.tony.sportsAnalytics.controller;

import com.tony.sportsAnalytics.service.BacktestingService;
import com.tony.sportsAnalytics.service.DataImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final DataImportService importService;
    private final BacktestingService backtestingService;

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
}
