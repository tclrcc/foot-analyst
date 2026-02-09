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
            return null; // Pas de données
        }

        // 1. Récupérer les stats les plus récentes
        MatchAnalysis lastMatch = history.getFirst();
        TeamStats baseStats = (lastMatch.getHomeTeam().equals(team))
                ? lastMatch.getHomeStats()
                : lastMatch.getAwayStats();

        // 2. Recalculer la forme
        int computedForm = calculateForm(team, history);

        // 3. Construction de l'objet de suggestion (Correction ici : Utilisation des setters)
        TeamStats suggestion = new TeamStats();

        suggestion.setRank(baseStats.getRank());
        suggestion.setPoints(baseStats.getPoints());

        // On transfère aussi les nouveaux champs de comptage s'ils existent
        suggestion.setMatchesPlayed(baseStats.getMatchesPlayed());
        suggestion.setMatchesPlayedHome(baseStats.getMatchesPlayedHome());
        suggestion.setMatchesPlayedAway(baseStats.getMatchesPlayedAway());

        suggestion.setGoalsFor(baseStats.getGoalsFor());
        suggestion.setGoalsAgainst(baseStats.getGoalsAgainst());
        suggestion.setXG(baseStats.getXG());

        // On injecte la forme recalculée
        suggestion.setLast5MatchesPoints(computedForm);

        return suggestion;
    }

    private int calculateForm(Team team, List<MatchAnalysis> history) {
        int points = 0;
        int matchesCounted = 0;

        for (MatchAnalysis m : history) {
            if (m.getHomeScore() != null && m.getAwayScore() != null) {
                boolean isHome = m.getHomeTeam().equals(team);
                int us = isHome ? m.getHomeScore() : m.getAwayScore();
                int them = isHome ? m.getAwayScore() : m.getHomeScore();

                if (us > them) points += 3;
                else if (us == them) points += 1;

                matchesCounted++;
                if (matchesCounted >= 5) break;
            }
        }
        return points;
    }
}
