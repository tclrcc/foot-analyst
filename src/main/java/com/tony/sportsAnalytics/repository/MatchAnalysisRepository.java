package com.tony.sportsAnalytics.repository;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchAnalysisRepository extends JpaRepository<MatchAnalysis, Long> {
    // Trouver les confrontations directes (H2H)
    // On cherche les matchs où (Home=A et Away=B) OU (Home=B et Away=A)
    @Query("SELECT m FROM MatchAnalysis m WHERE " +
            "(m.homeTeam = :t1 AND m.awayTeam = :t2) OR " +
            "(m.homeTeam = :t2 AND m.awayTeam = :t1) " +
            "ORDER BY m.matchDate DESC")
    List<MatchAnalysis> findH2H(@Param("t1") Team t1, @Param("t2") Team t2, Pageable pageable);

    List<MatchAnalysis> findByHomeTeamIdOrAwayTeamIdOrderByMatchDateDesc(Long homeId, Long awayId);

    @Query("SELECT m FROM MatchAnalysis m WHERE m.homeTeam.id = :teamId OR m.awayTeam.id = :teamId ORDER BY m.matchDate DESC")
    List<MatchAnalysis> findLastMatchesByTeam(@Param("teamId") Long teamId);

    // 2. Pour calculer les stats globales (Moyenne buts, etc.) d'une ligue sur une saison
    // Spring comprend automatiquement : Match -> HomeTeam -> League
    List<MatchAnalysis> findByHomeTeamLeagueAndSeason(League league, String season);

    // Récupère les 5 derniers matchs (Top 5) d'une équipe (Dom ou Ext),
    // où le score n'est pas null (donc match joué), triés du plus récent au plus vieux.
    List<MatchAnalysis> findTop5ByHomeTeamIdOrAwayTeamIdAndHomeScoreIsNotNullOrderByMatchDateDesc(Long homeTeamId, Long awayTeamId);

    // Récupère les matchs où le score domicile est NULL (donc non joués)
    @Query("SELECT m FROM MatchAnalysis m WHERE m.homeScore IS NULL ORDER BY m.matchDate ASC")
    List<MatchAnalysis> findUpcomingMatches();

    @Query("SELECT m FROM MatchAnalysis m WHERE m.homeTeam.id = :homeId AND m.awayTeam.id = :awayId")
    List<MatchAnalysis> findByTeamIds(@Param("homeId") Long homeId, @Param("awayId") Long awayId);
}
