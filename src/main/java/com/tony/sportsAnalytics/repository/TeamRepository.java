package com.tony.sportsAnalytics.repository;

import com.tony.sportsAnalytics.model.League;
import com.tony.sportsAnalytics.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team,Long> {
    Optional<Team> findByName(String name);

    List<Team> findByLeagueId(Long leagueId);

    List<Team> findByLeague(League league);

    // Cette méthode permet de chercher une équipe par son nom ET sa ligue spécifique.
    Optional<Team> findByNameAndLeague(String name, League league);
}
