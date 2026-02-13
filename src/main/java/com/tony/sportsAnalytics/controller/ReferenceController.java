package com.tony.sportsAnalytics.controller;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.model.TeamStats;
import com.tony.sportsAnalytics.repository.LeagueRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import com.tony.sportsAnalytics.service.TeamStatsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/references")
@RequiredArgsConstructor
public class ReferenceController {

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final TeamStatsService teamStatsService;

    @GetMapping("/leagues")
    public ResponseEntity<List<League>> getAllLeagues() {
        return ResponseEntity.ok(leagueRepository.findAll());
    }

    @GetMapping("/leagues/{leagueId}/teams")
    public ResponseEntity<List<Team>> getTeamsByLeague(@PathVariable Long leagueId) {
        return ResponseEntity.ok(teamRepository.findByLeagueId(leagueId));
    }

    @GetMapping("/teams/{teamId}/suggested-stats")
    public ResponseEntity<TeamStats> getSuggestedStats(@PathVariable Long teamId) {
        return ResponseEntity.ofNullable(teamStatsService.getSuggestedStats(teamId));
    }

    @PostMapping("/leagues")
    public ResponseEntity<?> createLeague(@RequestBody League league) {
        // 1. Contrôle doublon Championnat
        if (leagueRepository.findByName(league.getName()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ce championnat existe déjà !"));
        }
        return ResponseEntity.ok(leagueRepository.save(league));
    }

    @PostMapping("/teams")
    public ResponseEntity<?> createTeam(@RequestBody CreateTeamRequest request) {
        // 2. Contrôle doublon Équipe
        if (teamRepository.findByName(request.getName()).isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "L'équipe '" + request.getName() + "' existe déjà dans la base."));
        }

        League league = leagueRepository.findById(request.getLeagueId())
                .orElseThrow(() -> new RuntimeException("League not found"));

        Team newTeam = new Team(request.getName(), league);
        return ResponseEntity.ok(teamRepository.save(newTeam));
    }

    @PutMapping("/teams/{teamId}/stats")
    public ResponseEntity<Team> updateTeamStats(@PathVariable Long teamId, @RequestBody TeamStats newStats) {
        return teamRepository.findById(teamId)
                .map(team -> {
                    team.setCurrentStats(newStats); // On écrase les stats actuelles
                    return ResponseEntity.ok(teamRepository.save(team));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/teams/{id}")
    public ResponseEntity<Team> getTeamDetails(@PathVariable Long id) {
        return teamRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/leagues/{leagueId}/standings")
    public ResponseEntity<List<Team>> getLeagueStandings(@PathVariable Long leagueId) {
        List<Team> teams = teamRepository.findByLeagueId(leagueId);

        // On ne renvoie que les équipes qui ont un rang (donc classées dans la saison actuelle)
        List<Team> standings = teams.stream()
                .filter(t -> t.getCurrentStats() != null && t.getCurrentStats().getRank() != null)
                .sorted(Comparator.comparing(t -> t.getCurrentStats().getRank()))
                .toList();

        return ResponseEntity.ok(standings);
    }

    // Petit DTO interne pour simplifier la requête JSON de création d'équipe
    @Data
    public static class CreateTeamRequest {
        private String name;
        private Long leagueId;
    }
}
