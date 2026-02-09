package com.tony.sportsAnalytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "prediction.weights")
@Data
public class PredictionProperties {
    private double baseScore = 50.0;
    private double venueImportance = 10.0;
    private double rankImportance = 3.0;
    private double pointsImportance = 2.0;
    private double formImportance = 5.0;
    private double xgImportance = 10.0;
    private double goalDiffImportance = 1.5;
    private double leagueAvgGoals = 1.35; // Moyenne de buts par équipe par match
}
