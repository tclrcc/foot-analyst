package com.tony.sportsAnalytics.repository;

import com.tony.sportsAnalytics.model.MatchAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchAnalysisRepository extends JpaRepository<MatchAnalysis, Long> {
}
