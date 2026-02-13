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

        // Récupération de tous les matchs (joués et futurs) triés par date décroissante
        List<MatchAnalysis> matches = matchRepository.findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(teamId, teamId);

        // Initialisation d'un nouvel objet stats pour repartir de zéro (évite les résidus d'anciens calculs)
        TeamStats s = new TeamStats();
        initializeStats(s); // Méthode utilitaire pour mettre tous les compteurs à 0

        int matchCountForForm = 0;
        int formPoints = 0;

        for (MatchAnalysis m : matches) {
            // 1. Filtre de sécurité : on ne traite que les matchs de la saison actuelle avec un score
            if (m.getHomeScore() == null || m.getAwayScore() == null) continue;
            if (!"2025-2026".equals(m.getSeason())) continue;

            boolean isHome = m.getHomeTeam().getId().equals(teamId);
            int myScore = isHome ? m.getHomeScore() : m.getAwayScore();
            int oppScore = isHome ? m.getAwayScore() : m.getHomeScore();

            // --- STATS GLOBALES ---
            s.setMatchesPlayed(s.getMatchesPlayed() + 1);
            s.setGoalsFor(s.getGoalsFor() + myScore);
            s.setGoalsAgainst(s.getGoalsAgainst() + oppScore);

            // --- STATS PAR LIEU (DOMICILE / EXTÉRIEUR) ---
            if (isHome) {
                s.setMatchesPlayedHome(s.getMatchesPlayedHome() + 1);
                s.setGoalsForHome(s.getGoalsForHome() + myScore);
                s.setGoalsAgainstHome(s.getGoalsAgainstHome() + oppScore);
            } else {
                s.setMatchesPlayedAway(s.getMatchesPlayedAway() + 1);
                s.setGoalsForAway(s.getGoalsForAway() + myScore);
                s.setGoalsAgainstAway(s.getGoalsAgainstAway() + oppScore);
            }

            // --- LOGIQUE DES POINTS ET RÉSULTATS (V/N/D) ---
            if (myScore > oppScore) { // Victoire
                s.setWins(s.getWins() + 1);
                s.setPoints(s.getPoints() + 3);
                if (isHome) {
                    s.setWinsHome(s.getWinsHome() + 1);
                    s.setPointsHome(s.getPointsHome() + 3);
                } else {
                    s.setWinsAway(s.getWinsAway() + 1);
                    s.setPointsAway(s.getPointsAway() + 3);
                }
                if (matchCountForForm < 5) formPoints += 3;
            }
            else if (myScore == oppScore) { // Nul
                s.setDraws(s.getDraws() + 1);
                s.setPoints(s.getPoints() + 1);
                if (isHome) {
                    s.setDrawsHome(s.getDrawsHome() + 1);
                    s.setPointsHome(s.getPointsHome() + 1);
                } else {
                    s.setDrawsAway(s.getDrawsAway() + 1);
                    s.setPointsAway(s.getPointsAway() + 1);
                }
                if (matchCountForForm < 5) formPoints += 1;
            }
            else { // Défaite
                s.setLosses(s.getLosses() + 1);
                if (isHome) s.setLossesHome(s.getLossesHome() + 1);
                else s.setLossesAway(s.getLossesAway() + 1);
            }

            if (matchCountForForm < 5) matchCountForForm++;
        }

        // Mise à jour des points de forme (5 derniers matchs)
        s.setLast5MatchesPoints(formPoints);

        // Calcul des moyennes (xG, possession, corners, etc.)
        calculateSeasonAverages(teamId, s);

        // Calcul de la forme offensive/défensive (buts sur les 5 derniers)
        calculateDynamicForm(teamId, s);

        // Sauvegarde finale
        team.setCurrentStats(s);
        teamRepository.save(team);

        log.info("✅ Stats complètes recalculées pour : {} ({} Pts, {} V - {} N - {} D)",
                team.getName(), s.getPoints(), s.getWins(), s.getDraws(), s.getLosses());
    }

    /**
     * Initialise tous les compteurs à zéro pour éviter les NullPointerException
     * et garantir la précision des calculs.
     */
    private void initializeStats(TeamStats s) {
        s.setPoints(0); s.setMatchesPlayed(0); s.setWins(0); s.setDraws(0); s.setLosses(0);
        s.setGoalsFor(0); s.setGoalsAgainst(0);
        s.setPointsHome(0); s.setMatchesPlayedHome(0); s.setWinsHome(0); s.setDrawsHome(0); s.setLossesHome(0);
        s.setGoalsForHome(0); s.setGoalsAgainstHome(0);
        s.setPointsAway(0); s.setMatchesPlayedAway(0); s.setWinsAway(0); s.setDrawsAway(0); s.setLossesAway(0);
        s.setGoalsForAway(0); s.setGoalsAgainstAway(0);
        s.setLast5MatchesPoints(0);
    }

    private void calculateSeasonAverages(Long teamId, TeamStats target) {
        log.info("📊 DÉBUT CALCUL MOYENNES - Equipe ID: {}", teamId);
        List<MatchAnalysis> matches = matchRepository.findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(teamId, teamId);
        log.info("   -> Matchs trouvés dans l'historique : {}", matches.size());

        double totalXG = 0.0, totalShots = 0.0, totalSOT = 0.0, totalPossession = 0.0, totalCorners = 0.0, totalCrosses = 0.0;
        int countXg = 0, countStats = 0, countPossession = 0;

        for(MatchAnalysis m : matches) {
            if(!"2025-2026".equals(m.getSeason())) continue;
            if(m.getHomeScore() == null || m.getAwayScore() == null) continue; // On ignore les matchs futurs non joués

            boolean isHome = m.getHomeTeam().getId().equals(teamId);
            MatchDetailStats myStats = isHome ? m.getHomeMatchStats() : m.getAwayMatchStats();

            if(myStats != null) {
                log.debug("   -> Match analysé (ID: {}) - Tirs: {}, Poss: {}", m.getId(), myStats.getShots(), myStats.getPossession());
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
            } else {
                log.warn("   -> ⚠️ AVERTISSEMENT : myStats est NULL pour le match ID: {}", m.getId());
            }
        }

        // Assignation avec arrondi à 1 ou 2 décimales
        target.setXG(countXg > 0 ? (Math.round((totalXG / countXg) * 100.0) / 100.0) : 1.35);
        target.setAvgShots(countStats > 0 ? (Math.round((totalShots / countStats) * 10.0) / 10.0) : 0.0);
        target.setAvgShotsOnTarget(countStats > 0 ? (Math.round((totalSOT / countStats) * 10.0) / 10.0) : 0.0);
        target.setAvgCorners(countStats > 0 ? (Math.round((totalCorners / countStats) * 10.0) / 10.0) : 0.0);
        target.setAvgCrosses(countStats > 0 ? (Math.round((totalCrosses / countStats) * 10.0) / 10.0) : 0.0);
        if (countPossession > 0) {
            target.setAvgPossession(Math.round((totalPossession / countPossession) * 10.0) / 10.0);
        } else if (target.getAvgPossession() == null || target.getAvgPossession() == 0.0) {
            target.setAvgPossession(50.0); // Valeur neutre de secours plutôt que 0% (qui casse les algos)
        }

        log.info("✅ FIN CALCUL - Tirs Moyens: {} (sur {} matchs valides), Possession Moyenne: {}% (sur {} matchs valides)",
                target.getAvgShots(), countStats, target.getAvgPossession(), countPossession);
    }

    private void calculateDynamicForm(Long teamId, TeamStats target) {
        List<MatchAnalysis> last5 = matchRepository.findTop5ByHomeTeamIdOrAwayTeamIdAndHomeScoreIsNotNullOrderByMatchDateDesc(teamId, teamId);

        int gf5 = 0;
        int ga5 = 0;

        for (MatchAnalysis m : last5) {
            // --- 🛡️ SÉCURITÉ ANTI NULL-POINTER (AUTO-UNBOXING) ---
            if (m.getHomeScore() == null || m.getAwayScore() == null) {
                log.warn("⚠️ Match ID {} ignoré pour la forme : Score manquant (Dom: {}, Ext: {})",
                        m.getId(), m.getHomeScore(), m.getAwayScore());
                continue; // On passe au match suivant
            }

            boolean isHome = m.getHomeTeam().getId().equals(teamId);
            gf5 += isHome ? m.getHomeScore() : m.getAwayScore();
            ga5 += isHome ? m.getAwayScore() : m.getHomeScore();
        }

        target.setGoalsForLast5(gf5);
        target.setGoalsAgainstLast5(ga5);
    }
}
