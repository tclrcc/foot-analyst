package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.model.TeamStats;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamStatsService {

    private final MatchAnalysisRepository matchRepository;
    private final TeamRepository teamRepository;

    public TeamStats getSuggestedStats(Long teamId) {
        Team team = teamRepository.findById(teamId).orElseThrow();

        // Récupérer les 10 derniers matchs pour l'analyse
        List<MatchAnalysis> history = matchRepository.findLatestMatchesByTeam(team, PageRequest.of(0, 10));

        if (history.isEmpty()) {
            return null; // Pas de données, le front laissera vide
        }

        // 1. Récupérer les stats les plus récentes (Rank, Points, xG)
        // On prend le dernier match analysé comme base
        MatchAnalysis lastMatch = history.getFirst();
        TeamStats baseStats = (lastMatch.getHomeTeam().equals(team))
                ? lastMatch.getHomeStats()
                : lastMatch.getAwayStats();

        // 2. Recalculer la forme (5 derniers matchs joués)
        int computedForm = calculateForm(team, history);

        // On renvoie un mix : stats statiques du dernier match + forme recalculée
        return new TeamStats(
                baseStats.getRank(),
                baseStats.getPoints(), // Idéalement, on devrait ajouter les points des matchs joués depuis
                baseStats.getGoalsFor(),
                baseStats.getGoalsAgainst(),
                baseStats.getXG(),
                computedForm
        );
    }

    private int calculateForm(Team team, List<MatchAnalysis> history) {
        int points = 0;
        int matchesCounted = 0;

        for (MatchAnalysis m : history) {
            // On ne compte que les matchs TERMINÉS (avec un score)
            if (m.getHomeScore() != null && m.getAwayScore() != null) {
                boolean isHome = m.getHomeTeam().equals(team);
                int us = isHome ? m.getHomeScore() : m.getAwayScore();
                int them = isHome ? m.getAwayScore() : m.getHomeScore();

                if (us > them) points += 3;
                else if (us == them) points += 1;

                matchesCounted++;
                if (matchesCounted >= 5) break; // On s'arrête à 5 matchs
            }
        }
        return points;
    }
}
