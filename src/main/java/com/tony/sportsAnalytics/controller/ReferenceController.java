package com.tony.sportsAnalytics.controller;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.model.TeamStats;
import com.tony.sportsAnalytics.repository.LeagueRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import com.tony.sportsAnalytics.service.TeamStatsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<League> createLeague(@RequestBody League league) {
        // Idéalement, gérer l'exception si le nom existe déjà
        return ResponseEntity.ok(leagueRepository.save(league));
    }

    @PostMapping("/teams")
    public ResponseEntity<Team> createTeam(@RequestBody CreateTeamRequest request) {
        League league = leagueRepository.findById(request.getLeagueId())
                .orElseThrow(() -> new RuntimeException("League not found"));

        Team newTeam = new Team(request.getName(), league);
        return ResponseEntity.ok(teamRepository.save(newTeam));
    }

    // Petit DTO interne pour simplifier la requête JSON de création d'équipe
    @Data
    public static class CreateTeamRequest {
        private String name;
        private Long leagueId;
    }
}
