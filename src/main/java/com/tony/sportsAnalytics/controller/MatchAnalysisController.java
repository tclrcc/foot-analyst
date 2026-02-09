package com.tony.sportsAnalytics.controller;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.dto.DashboardStats;
import com.tony.sportsAnalytics.model.dto.MatchAnalysisRequest;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.service.DashboardService;
import com.tony.sportsAnalytics.service.MatchAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor // Injection de dépendance par constructeur (Best Practice)
public class MatchAnalysisController {
    private final MatchAnalysisService matchAnalysisService;
    private final DashboardService dashboardService;
    private final MatchAnalysisRepository repository;

    @GetMapping
    public ResponseEntity<List<MatchAnalysis>> getAllAnalyses() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<MatchAnalysis> createAnalysis(@Valid @RequestBody MatchAnalysisRequest request) {
        return ResponseEntity.ok(matchAnalysisService.analyzeAndSave(request));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        return ResponseEntity.ok(dashboardService.getKpi());
    }
}
