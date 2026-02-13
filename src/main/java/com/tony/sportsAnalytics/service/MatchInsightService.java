package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class MatchInsightService {

    /**
     * Génère une liste de faits marquants pour un match donné.
     */
    public List<String> generateKeyFacts(Team home, Team away, List<MatchAnalysis> homeHistory, List<MatchAnalysis> awayHistory) {
        List<String> facts = new ArrayList<>();

        facts.add(analyzeStreak(home, homeHistory));
        facts.add(analyzeStreak(away, awayHistory));
        facts.add(analyzeBttsTrend(home, homeHistory));
        facts.add(analyzeBttsTrend(away, awayHistory));
        facts.add(analyzeVenueSpecificDraws(away, awayHistory, false));

        // SOLUTION : On collecte explicitement dans une ArrayList (mutable)
        return facts.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Algorithme de détection de séries (Victoires consécutives, ou Invincibilité).
     */
    private String analyzeStreak(Team team, List<MatchAnalysis> history) {
        if (history == null || history.isEmpty()) return null;

        int unbeatenStreak = 0;
        int losingStreak = 0;

        for (MatchAnalysis m : history) {
            if (m.getHomeScore() == null) continue; // On ignore les matchs non joués

            boolean isHome = m.getHomeTeam().equals(team);
            int goalsFor = isHome ? m.getHomeScore() : m.getAwayScore();
            int goalsAgainst = isHome ? m.getAwayScore() : m.getHomeScore();

            if (goalsFor >= goalsAgainst) {
                if (losingStreak > 0) break; // Fin de la série de défaites
                unbeatenStreak++;
            } else {
                if (unbeatenStreak > 0) break; // Fin de la série d'invincibilité
                losingStreak++;
            }
        }

        // On ne remonte l'info à l'utilisateur que si elle est statistiquement significative (>= 5 matchs)
        if (unbeatenStreak >= 5) {
            return "🔥 " + team.getName() + " est invaincu depuis " + unbeatenStreak + " matchs.";
        } else if (losingStreak >= 4) {
            return "⚠️ " + team.getName() + " reste sur " + losingStreak + " défaites consécutives.";
        }
        return null;
    }

    /**
     * Calcule le pourcentage de matchs où les deux équipes marquent (BTTS).
     */
    private String analyzeBttsTrend(Team team, List<MatchAnalysis> history) {
        if (history == null || history.size() < 5) return null;

        long bttsCount = history.stream()
                .filter(m -> m.getHomeScore() != null && m.getAwayScore() != null)
                .filter(m -> m.getHomeScore() > 0 && m.getAwayScore() > 0)
                .count();

        double bttsPct = ((double) bttsCount / history.size()) * 100.0;

        if (bttsPct >= 80.0) {
            return "⚽ " + Math.round(bttsPct) + "% de BTTS dans les matchs récents de " + team.getName() + ".";
        } else if (bttsPct <= 20.0) {
            return "🛡️ Très peu de buts des deux côtés (" + Math.round(bttsPct) + "% de BTTS) pour " + team.getName() + ".";
        }
        return null;
    }

    /**
     * Analyse des performances spécifiques selon le lieu (Dom/Ext).
     */
    private String analyzeVenueSpecificDraws(Team team, List<MatchAnalysis> history, boolean isHomeContext) {
        if (history == null) return null;

        int matchCount = 0;
        int draws = 0;

        for (MatchAnalysis m : history) {
            if (m.getHomeScore() == null) continue;

            boolean isActuallyHome = m.getHomeTeam().equals(team);
            // On ne regarde que les matchs joués dans la même configuration (Extérieur ou Domicile)
            if (isActuallyHome != isHomeContext) continue;

            matchCount++;
            if (m.getHomeScore().equals(m.getAwayScore())) draws++;
        }

        if (matchCount >= 5 && draws == 0) {
            String venue = isHomeContext ? "à domicile" : "à l'extérieur";
            return "🎯 " + team.getName() + " a fait 0 match nul sur ses " + matchCount + " derniers matchs " + venue + ".";
        }
        return null;
    }
}
