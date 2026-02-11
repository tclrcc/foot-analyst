package com.tony.sportsAnalytics.controller;

import com.tony.sportsAnalytics.service.DataImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DataImportService importService;

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
}
