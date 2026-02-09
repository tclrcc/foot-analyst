package com.tony.sportsAnalytics.controller;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.repository.LeagueRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
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

    @GetMapping("/leagues")
    public ResponseEntity<List<League>> getAllLeagues() {
        return ResponseEntity.ok(leagueRepository.findAll());
    }

    @GetMapping("/leagues/{leagueId}/teams")
    public ResponseEntity<List<Team>> getTeamsByLeague(@PathVariable Long leagueId) {
        return ResponseEntity.ok(teamRepository.findByLeagueId(leagueId));
    }
}
