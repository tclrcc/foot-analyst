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

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // V11 : Get matchs d'une équipe
    @GetMapping("/teams/{teamId}")
    public List<MatchAnalysis> getTeamMatches(@PathVariable Long teamId) {
        return matchAnalysisService.getMatchesByTeam(teamId);
    }

    // V11 : Update match
    @PutMapping("/{id}")
    public ResponseEntity<MatchAnalysis> updateMatch(@PathVariable Long id, @RequestBody MatchAnalysisRequest request) {
        return ResponseEntity.ok(matchAnalysisService.updateMatch(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchAnalysis> getAnalysis(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<MatchAnalysis>> getUpcomingMatches() {
        LocalDateTime todayMidnight = LocalDate.now().atStartOfDay();
        return ResponseEntity.ok(repository.findUpcomingMatches(todayMidnight));
    }

    @GetMapping("/{matchId}/h2h")
    public ResponseEntity<List<MatchAnalysis>> getH2H(
            @PathVariable Long matchId,
            @RequestParam(defaultValue = "ALL") String filter // "ALL" ou "HOME"
    ) {
        // 1. Récupérer le match actuel pour savoir qui sont les équipes
        MatchAnalysis currentMatch = repository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match non trouvé"));

        Long homeId = currentMatch.getHomeTeam().getId();
        Long awayId = currentMatch.getAwayTeam().getId();

        List<MatchAnalysis> history;

        // 2. Appliquer le filtre
        if ("HOME".equalsIgnoreCase(filter)) {
            // Historique seulement quand l'équipe A recevait l'équipe B
            history = repository.findHeadToHeadHome(homeId, awayId);
        } else {
            // Historique global (domicile et extérieur)
            history = repository.findHeadToHeadGlobal(homeId, awayId);
        }

        // On exclut le match actuel de l'historique (s'il est déjà en base)
        history.removeIf(m -> m.getId().equals(matchId));

        return ResponseEntity.ok(history);
    }
}
