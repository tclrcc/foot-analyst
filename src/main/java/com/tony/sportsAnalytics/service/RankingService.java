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

            // Sécurité absolue (Remplacement des null par 0)
            int p1 = type.equals("HOME") ? (s1.getPointsHome() != null ? s1.getPointsHome() : 0) :
                    (type.equals("AWAY") ? (s1.getPointsAway() != null ? s1.getPointsAway() : 0) :
                            (s1.getPoints() != null ? s1.getPoints() : 0));

            int p2 = type.equals("HOME") ? (s2.getPointsHome() != null ? s2.getPointsHome() : 0) :
                    (type.equals("AWAY") ? (s2.getPointsAway() != null ? s2.getPointsAway() : 0) :
                            (s2.getPoints() != null ? s2.getPoints() : 0));

            if (p1 != p2) return Integer.compare(p2, p1);

            int gf1 = type.equals("HOME") ? (s1.getGoalsForHome() != null ? s1.getGoalsForHome() : 0) :
                    (type.equals("AWAY") ? (s1.getGoalsForAway() != null ? s1.getGoalsForAway() : 0) :
                            (s1.getGoalsFor() != null ? s1.getGoalsFor() : 0));

            int ga1 = type.equals("HOME") ? (s1.getGoalsAgainstHome() != null ? s1.getGoalsAgainstHome() : 0) :
                    (type.equals("AWAY") ? (s1.getGoalsAgainstAway() != null ? s1.getGoalsAgainstAway() : 0) :
                            (s1.getGoalsAgainst() != null ? s1.getGoalsAgainst() : 0));

            int gf2 = type.equals("HOME") ? (s2.getGoalsForHome() != null ? s2.getGoalsForHome() : 0) :
                    (type.equals("AWAY") ? (s2.getGoalsForAway() != null ? s2.getGoalsForAway() : 0) :
                            (s2.getGoalsFor() != null ? s2.getGoalsFor() : 0));

            int ga2 = type.equals("HOME") ? (s2.getGoalsAgainstHome() != null ? s2.getGoalsAgainstHome() : 0) :
                    (type.equals("AWAY") ? (s2.getGoalsAgainstAway() != null ? s2.getGoalsAgainstAway() : 0) :
                            (s2.getGoalsAgainst() != null ? s2.getGoalsAgainst() : 0));

            int gd1 = gf1 - ga1;
            int gd2 = gf2 - ga2;

            if (gd1 != gd2) return Integer.compare(gd2, gd1);
            return Integer.compare(gf2, gf1);
        });
    }
}
