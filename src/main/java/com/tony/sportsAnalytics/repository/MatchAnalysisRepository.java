package com.tony.sportsAnalytics.repository;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import com.tony.sportsAnalytics.model.Team;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchAnalysisRepository extends JpaRepository<MatchAnalysis, Long> {
    // Trouve les matchs (passés ou futurs) impliquant l'équipe, triés par date décroissante
    @Query("SELECT m FROM MatchAnalysis m WHERE m.homeTeam = :team OR m.awayTeam = :team ORDER BY m.matchDate DESC")
    List<MatchAnalysis> findLatestMatchesByTeam(@Param("team") Team team, Pageable pageable);

    // Trouver les confrontations directes (H2H)
    // On cherche les matchs où (Home=A et Away=B) OU (Home=B et Away=A)
    @Query("SELECT m FROM MatchAnalysis m WHERE " +
            "(m.homeTeam = :t1 AND m.awayTeam = :t2) OR " +
            "(m.homeTeam = :t2 AND m.awayTeam = :t1) " +
            "ORDER BY m.matchDate DESC")
    List<MatchAnalysis> findH2H(@Param("t1") Team t1, @Param("t2") Team t2, Pageable pageable);
}
