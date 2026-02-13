package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RankingService {

    private final TeamRepository teamRepository;

    /**
     * Calcule et met à jour le classement de toutes les équipes d'une ligue.
     * Doit être appelé APRÈS le recalcul des statistiques individuelles (TeamStatsService).
     */
    @Transactional
    public void updateLeagueRankings(Long leagueId) {
        List<Team> teams = teamRepository.findByLeagueId(leagueId)
                .stream().filter(t -> t.getCurrentStats() != null && t.getCurrentStats().getMatchesPlayed() > 0)
                .collect(Collectors.toList());

        if (teams.isEmpty()) return;

        // 1. Classement Général
        sortTeams(teams, "GENERAL");
        for (int i = 0; i < teams.size(); i++) teams.get(i).getCurrentStats().setRank(i + 1);

        // 2. Classement Domicile
        sortTeams(teams, "HOME");
        for (int i = 0; i < teams.size(); i++) teams.get(i).getCurrentStats().setRankHome(i + 1);

        // 3. Classement Extérieur
        sortTeams(teams, "AWAY");
        for (int i = 0; i < teams.size(); i++) teams.get(i).getCurrentStats().setRankAway(i + 1);

        teamRepository.saveAll(teams);
    }

    private void sortTeams(List<Team> teams, String type) {
        teams.sort((t1, t2) -> {
            var s1 = t1.getCurrentStats();
            var s2 = t2.getCurrentStats();

            int p1 = type.equals("HOME") ? s1.getPointsHome() : (type.equals("AWAY") ? s1.getPointsAway() : s1.getPoints());
            int p2 = type.equals("HOME") ? s2.getPointsHome() : (type.equals("AWAY") ? s2.getPointsAway() : s2.getPoints());

            if (p1 != p2) return Integer.compare(p2, p1);

            int gf1 = type.equals("HOME") ? s1.getGoalsForHome() : (type.equals("AWAY") ? s1.getGoalsForAway() : s1.getGoalsFor());
            int ga1 = type.equals("HOME") ? s1.getGoalsAgainstHome() : (type.equals("AWAY") ? s1.getGoalsAgainstAway() : s1.getGoalsAgainst());
            int gf2 = type.equals("HOME") ? s2.getGoalsForHome() : (type.equals("AWAY") ? s2.getGoalsForAway() : s2.getGoalsFor());
            int ga2 = type.equals("HOME") ? s2.getGoalsAgainstHome() : (type.equals("AWAY") ? s2.getGoalsAgainstAway() : s2.getGoalsAgainst());

            int gd1 = gf1 - ga1;
            int gd2 = gf2 - ga2;

            if (gd1 != gd2) return Integer.compare(gd2, gd1);
            return Integer.compare(gf2, gf1);
        });
    }
}
