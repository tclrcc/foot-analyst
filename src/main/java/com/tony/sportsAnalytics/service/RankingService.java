package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        // 1. Récupérer toutes les équipes de la ligue
        List<Team> teams = teamRepository.findByLeagueId(leagueId);

        if (teams.isEmpty()) return;

        // 2. Trier selon les règles standards du football :
        // Points (Desc) -> Différence de buts (Desc) -> Buts marqués (Desc)
        teams.sort((t1, t2) -> {
            var stats1 = t1.getCurrentStats();
            var stats2 = t2.getCurrentStats();

            // Sécurité anti-NullPointer
            if (stats1 == null && stats2 == null) return 0;
            if (stats1 == null) return 1; // Les équipes sans stats finissent en bas
            if (stats2 == null) return -1;

            int pts1 = stats1.getPoints() != null ? stats1.getPoints() : 0;
            int pts2 = stats2.getPoints() != null ? stats2.getPoints() : 0;

            // Critère 1 : Points
            if (pts1 != pts2) {
                return Integer.compare(pts2, pts1); // Ordre décroissant
            }

            // Critère 2 : Différence de buts (Goal Difference)
            int gd1 = (stats1.getGoalsFor() != null ? stats1.getGoalsFor() : 0)
                    - (stats1.getGoalsAgainst() != null ? stats1.getGoalsAgainst() : 0);
            int gd2 = (stats2.getGoalsFor() != null ? stats2.getGoalsFor() : 0)
                    - (stats2.getGoalsAgainst() != null ? stats2.getGoalsAgainst() : 0);

            if (gd1 != gd2) {
                return Integer.compare(gd2, gd1); // Ordre décroissant
            }

            // Critère 3 : Buts marqués (Goals For)
            int gf1 = stats1.getGoalsFor() != null ? stats1.getGoalsFor() : 0;
            int gf2 = stats2.getGoalsFor() != null ? stats2.getGoalsFor() : 0;

            return Integer.compare(gf2, gf1); // Ordre décroissant
        });

        // 3. Assigner le rang officiel
        int rank = 1;
        for (Team team : teams) {
            if (team.getCurrentStats() != null) {
                team.getCurrentStats().setRank(rank++);
            }
        }

        // 4. Sauvegarder en lot (très performant via Hibernate batching)
        teamRepository.saveAll(teams);
        log.info("🏆 Classement généré et mis à jour pour la ligue ID {}", leagueId);
    }
}
