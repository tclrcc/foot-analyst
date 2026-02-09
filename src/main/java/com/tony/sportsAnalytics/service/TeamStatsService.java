package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.MatchDetailStats;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.model.TeamStats;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Recalcule toutes les stats d'une équipe en fonction de son historique de matchs.
     */
    @Transactional
    public void recalculateTeamStats(Long teamId) {
        Team team = teamRepository.findById(teamId).orElseThrow();

        // 1. Récupérer tous les matchs joués (exclure les futurs)
        List<MatchAnalysis> matches = matchRepository.findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(teamId, teamId)
                .stream()
                .filter(m -> m.getHomeScore() != null && m.getAwayScore() != null) // Seulement matchs finis
                .toList();

        if (matches.isEmpty()) return;

        TeamStats stats = new TeamStats();

        // --- STATS SAISON (GLOBAL) ---
        int points = 0;
        int goalsFor = 0;
        int goalsAgainst = 0;
        int wins = 0, draws = 0, losses = 0;
        double totalXg = 0.0;
        int xgCount = 0;

        // Pour l'avantage domicile
        int venuePlayed = 0;
        int venuePoints = 0;

        // --- STATS FORME (5 DERNIERS) ---
        int formGF = 0;
        int formGA = 0;
        int formPoints = 0;
        int matchesCounter = 0;

        for (MatchAnalysis m : matches) {
            boolean isHome = m.getHomeTeam().getId().equals(teamId);
            int scoreF = isHome ? m.getHomeScore() : m.getAwayScore();
            int scoreA = isHome ? m.getAwayScore() : m.getHomeScore();
            MatchDetailStats detail = isHome ? m.getHomeMatchStats() : m.getAwayMatchStats();

            // Cumul Saison
            goalsFor += scoreF;
            goalsAgainst += scoreA;

            int pts = 0;
            if (scoreF > scoreA) { pts = 3; wins++; }
            else if (scoreF == scoreA) { pts = 1; draws++; }
            else { losses++; }
            points += pts;

            // Stats Domicile (si l'équipe jouait à domicile ce match-là)
            if (isHome) {
                venuePlayed++;
                venuePoints += pts;
            }

            // xG Moyen
            if (detail != null && detail.getXG() != null) {
                totalXg += detail.getXG();
                xgCount++;
            }

            // Cumul Forme (5 derniers matchs seulement)
            // Comme la liste est triée par date décroissante (DESC), les 5 premiers sont les plus récents
            if (matchesCounter < 5) {
                formGF += scoreF;
                formGA += scoreA;
                formPoints += pts;
                matchesCounter++;
            }
        }

        // --- MAPPING VERS L'OBJET ---
        stats.setMatchesPlayed(matches.size());
        stats.setPoints(points);
        stats.setGoalsFor(goalsFor);
        stats.setGoalsAgainst(goalsAgainst);

        // Calcul Moyenne xG
        if (xgCount > 0) {
            stats.setXG(Math.round((totalXg / xgCount) * 100.0) / 100.0);
        } else {
            stats.setXG(1.35); // Valeur par défaut si pas de stats
        }

        // Stats Forme
        stats.setGoalsForLast5(formGF);
        stats.setGoalsAgainstLast5(formGA);
        stats.setLast5MatchesPoints(formPoints);

        // Stats Venue (Domicile) - Tu pourrais stocker Extérieur aussi si besoin
        stats.setVenueMatches(venuePlayed);
        stats.setVenuePoints(venuePoints);

        // Mise à jour simplifiée des MJ Domicile/Extérieur (approx)
        stats.setMatchesPlayedHome(venuePlayed);
        stats.setMatchesPlayedAway(matches.size() - venuePlayed);

        // Sauvegarde
        team.setCurrentStats(stats);
        teamRepository.save(team);

        System.out.println("✅ Stats recalculées pour : " + team.getName());
    }
}
