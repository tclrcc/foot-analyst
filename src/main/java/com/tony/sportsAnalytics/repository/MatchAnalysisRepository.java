package com.tony.sportsAnalytics.repository;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchAnalysisRepository extends JpaRepository<MatchAnalysis, Long> {
    // 1. Correction H2H avec CAST pour PostgreSQL et filtre de date
    @Query("SELECT m FROM MatchAnalysis m WHERE " +
            "((m.homeTeam = :t1 AND m.awayTeam = :t2) OR (m.homeTeam = :t2 AND m.awayTeam = :t1)) " +
            "AND (CAST(:date AS timestamp) IS NULL OR m.matchDate < :date) " +
            "ORDER BY m.matchDate DESC")
    List<MatchAnalysis> findH2H(@Param("t1") Team t1, @Param("t2") Team t2, @Param("date") LocalDateTime date);

    // 2. Correction Historique Équipe avec CAST et filtre de date
    // On remplace la méthode par défaut par une version qui gère la chronologie
    @Query("SELECT m FROM MatchAnalysis m WHERE " +
            "(m.homeTeam.id = :teamId OR m.awayTeam.id = :teamId) " +
            "AND (CAST(:date AS timestamp) IS NULL OR m.matchDate < :date) " +
            "ORDER BY m.matchDate DESC")
    List<MatchAnalysis> findLastMatchesByTeam(@Param("teamId") Long teamId, @Param("date") LocalDateTime date);

    List<MatchAnalysis> findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(Long homeId, Long awayId);

    // 2. Pour calculer les stats globales (Moyenne buts, etc.) d'une ligue sur une saison
    // Spring comprend automatiquement : Match -> HomeTeam -> League
    List<MatchAnalysis> findByHomeTeamLeagueAndSeason(League league, String season);

    // Récupère les 5 derniers matchs (Top 5) d'une équipe (Dom ou Ext),
    // où le score n'est pas null (donc match joué), triés du plus récent au plus vieux.
    List<MatchAnalysis> findTop5ByHomeTeamIdOrAwayTeamIdAndHomeScoreIsNotNullOrderByMatchDateDesc(Long homeTeamId, Long awayTeamId);

    // Récupère les matchs où le score domicile est NULL (donc non joués)
    @Query("SELECT m FROM MatchAnalysis m WHERE m.homeScore IS NULL AND m.matchDate >= :fromDate ORDER BY m.matchDate ASC")
    List<MatchAnalysis> findUpcomingMatches(@Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT m FROM MatchAnalysis m WHERE m.homeTeam.id = :homeId AND m.awayTeam.id = :awayId")
    List<MatchAnalysis> findByTeamIds(@Param("homeId") Long homeId, @Param("awayId") Long awayId);

    // 1. Tous les matchs (H2H Global)
    // On cherche tous les matchs joués entre T1 et T2, peu importe qui recevait
    @Query("SELECT m FROM MatchAnalysis m WHERE " +
            "(m.homeTeam.id = :t1Id AND m.awayTeam.id = :t2Id) OR " +
            "(m.homeTeam.id = :t2Id AND m.awayTeam.id = :t1Id) " +
            "ORDER BY m.matchDate DESC")
    List<MatchAnalysis> findHeadToHeadGlobal(@Param("t1Id") Long team1Id, @Param("t2Id") Long team2Id);

    // 2. Matchs à domicile (H2H Venue specific)
    // On cherche uniquement quand T1 recevait T2
    @Query("SELECT m FROM MatchAnalysis m WHERE " +
            "m.homeTeam.id = :homeId AND m.awayTeam.id = :awayId " +
            "ORDER BY m.matchDate DESC")
    List<MatchAnalysis> findHeadToHeadHome(@Param("homeId") Long homeId, @Param("awayId") Long awayId);

    // Ajoute cette méthode dans l'interface
    List<MatchAnalysis> findByMatchDateBetweenOrderByMatchDateAsc(LocalDateTime start, LocalDateTime end);

    @Query("SELECT m FROM MatchAnalysis m WHERE m.homeTeam.league.id = :leagueId AND m.homeScore IS NOT NULL ORDER BY m.matchDate ASC")
    List<MatchAnalysis> findFinishedMatchesByLeague(@Param("leagueId") Long leagueId);
}
