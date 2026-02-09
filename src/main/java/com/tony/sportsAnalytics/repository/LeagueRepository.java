package com.tony.sportsAnalytics.repository;

import com.tony.sportsAnalytics.model.League;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeagueRepository extends JpaRepository<League, Long> {
    Optional<League> findByName(String name);
}
