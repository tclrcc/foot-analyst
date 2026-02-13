package com.tony.sportsAnalytics.service;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.MatchDetailStats;
import com.tony.sportsAnalytics.model.Team;
import com.tony.sportsAnalytics.model.TeamStats;
import com.tony.sportsAnalytics.repository.MatchAnalysisRepository;
import com.tony.sportsAnalytics.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamStatsService {

    private final TeamRepository teamRepository;
    private final MatchAnalysisRepository matchRepository;

    /**
     * Renvoie les stats suggérées pour l'analyse (pré-remplissage IHM).
     * Combine les stats "officielles" (TeamStats stocké) + un calcul dynamique récent.
     */
    public TeamStats getSuggestedStats(Long teamId) {
        // 1. Récupérer l'objet TeamStats stocké (s'il existe), sinon un objet vide
        // C'est ici que tu avais le bug (team.getStats() renvoyait null)
        Team team = teamRepository.findById(teamId).orElseThrow();
        TeamStats baseStats = team.getCurrentStats();

        // --- CORRECTION DU NULL POINTER ---
        if (baseStats == null) {
            baseStats = new TeamStats(); // On part de zéro si pas de stats
            // Initialisation des valeurs par défaut pour éviter d'autres NPE
            baseStats.setRank(0);
            baseStats.setPoints(0);
            baseStats.setGoalsFor(0);
            baseStats.setGoalsAgainst(0);
            baseStats.setMatchesPlayed(0);
            baseStats.setXG(0.0);
        }

        // 2. Créer une copie pour ne pas modifier l'entité JPA directement
        TeamStats suggested = new TeamStats();
        suggested.setRank(baseStats.getRank());
        suggested.setPoints(baseStats.getPoints());
        suggested.setGoalsFor(baseStats.getGoalsFor());
        suggested.setGoalsAgainst(baseStats.getGoalsAgainst());
        suggested.setMatchesPlayed(baseStats.getMatchesPlayed());

        // 3. Calcul Dynamique "Forme Récente" (5 derniers matchs)
        // (Logique conservée telle quelle ou améliorée ci-dessous)
        calculateDynamicForm(teamId, suggested);

        // 4. Calcul xG Moyen sur la saison (Dynamique)
        // Si l'xG n'est pas dans baseStats (car import CSV sans xG), on le calcule
        calculateSeasonAverages(teamId, suggested);

        return suggested;
    }

    /**
     * Recalcule TOUTES les stats d'une équipe depuis l'historique des matchs.
     * À appeler après un import massif.
     */
    @Transactional
    public void recalculateTeamStats(Long teamId) {
        Team team = teamRepository.findById(teamId).orElseThrow();

        // On récupère tous les matchs JOUÉS (avec score) de la saison
        List<MatchAnalysis> matches = matchRepository.findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(teamId, teamId);

        int played = 0;
        int points = 0;
        int gf = 0;
        int ga = 0;

        // Variables pour la forme (5 derniers)
        int formPoints = 0;
        int matchCount = 0;

        for (MatchAnalysis m : matches) {
            // Ignorer les matchs futurs (sans score)
            if (m.getHomeScore() == null || m.getAwayScore() == null) continue;

            // Filtre Saison (Optionnel, ici on prend tout, à affiner si besoin)
            if (!"2025-2026".equals(m.getSeason())) continue;

            played++;
            boolean isHome = m.getHomeTeam().getId().equals(teamId);

            int myScore = isHome ? m.getHomeScore() : m.getAwayScore();
            int oppScore = isHome ? m.getAwayScore() : m.getHomeScore();

            gf += myScore;
            ga += oppScore;

            if (myScore > oppScore) points += 3;
            else if (myScore == oppScore) points += 1;

            // Calcul Forme (5 derniers matchs)
            if (matchCount < 5) {
                if (myScore > oppScore) formPoints += 3;
                else if (myScore == oppScore) formPoints += 1;
                matchCount++;
            }
        }

        // Mise à jour ou Création
        TeamStats stats = team.getCurrentStats();
        if (stats == null) {
            stats = new TeamStats();
            team.setCurrentStats(stats); // On lie les deux
        }

        stats.setMatchesPlayed(played);
        stats.setPoints(points);
        stats.setGoalsFor(gf);
        stats.setGoalsAgainst(ga);
        // Note: Le "Rank" est dur à calculer sans connaitre les autres équipes.
        // On le laisse tel quel ou on met 0.
        // stats.setRank(...);

        // Stockage Forme dans un champ transitoire ou détourné (ex: Last5MatchesPoints si tu l'as ajouté)
        // Pour l'instant on ne stocke pas la forme dans TeamStats (entité), mais on la calcule à la volée.
        calculateSeasonAverages(teamId, stats);

        teamRepository.save(team);
        log.info("✅ Stats recalculées pour : {} ({} Pts, {} MJ)", team.getName(), points, played);
    }

    private void calculateSeasonAverages(Long teamId, TeamStats target) {
        List<MatchAnalysis> matches = matchRepository.findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(teamId, teamId);

        double totalXG = 0.0, totalShots = 0.0, totalSOT = 0.0, totalPossession = 0.0, totalCorners = 0.0, totalCrosses = 0.0;
        int countXg = 0, countStats = 0, countPossession = 0;

        for(MatchAnalysis m : matches) {
            if(!"2025-2026".equals(m.getSeason())) continue;
            if(m.getHomeScore() == null) continue; // On ignore les matchs futurs non joués

            boolean isHome = m.getHomeTeam().getId().equals(teamId);
            MatchDetailStats myStats = isHome ? m.getHomeMatchStats() : m.getAwayMatchStats();

            if(myStats != null) {
                // xG
                if (myStats.getXG() != null) {
                    totalXG += myStats.getXG();
                    countXg++;
                } else if (myStats.getShots() != null) { // Fallback
                    totalXG += (myStats.getShots() * 0.10);
                    countXg++;
                }

                // Volume de jeu offensif
                if (myStats.getShots() != null) {
                    totalShots += myStats.getShots();
                    if (myStats.getShotsOnTarget() != null) totalSOT += myStats.getShotsOnTarget();
                    if (myStats.getCorners() != null) totalCorners += myStats.getCorners();
                    if (myStats.getCrosses() != null) totalCrosses += myStats.getCrosses();
                    countStats++;
                }

                // Possession
                if (myStats.getPossession() != null) {
                    totalPossession += myStats.getPossession();
                    countPossession++;
                }
            }
        }

        // Assignation avec arrondi à 1 ou 2 décimales
        target.setXG(countXg > 0 ? (Math.round((totalXG / countXg) * 100.0) / 100.0) : 1.35);
        target.setAvgShots(countStats > 0 ? (Math.round((totalShots / countStats) * 10.0) / 10.0) : 0.0);
        target.setAvgShotsOnTarget(countStats > 0 ? (Math.round((totalSOT / countStats) * 10.0) / 10.0) : 0.0);
        target.setAvgCorners(countStats > 0 ? (Math.round((totalCorners / countStats) * 10.0) / 10.0) : 0.0);
        target.setAvgCrosses(countStats > 0 ? (Math.round((totalCrosses / countStats) * 10.0) / 10.0) : 0.0);
        target.setAvgPossession(countPossession > 0 ? (Math.round((totalPossession / countPossession) * 10.0) / 10.0) : 0.0);
    }

    private void calculateDynamicForm(Long teamId, TeamStats target) {
        List<MatchAnalysis> last5 = matchRepository.findTop5ByHomeTeamIdOrAwayTeamIdAndHomeScoreIsNotNullOrderByMatchDateDesc(teamId, teamId);

        int gf5 = 0;
        int ga5 = 0;

        for (MatchAnalysis m : last5) {
            boolean isHome = m.getHomeTeam().getId().equals(teamId);
            gf5 += isHome ? m.getHomeScore() : m.getAwayScore();
            ga5 += isHome ? m.getAwayScore() : m.getHomeScore();
        }

        target.setGoalsForLast5(gf5);
        target.setGoalsAgainstLast5(ga5);
    }
}
