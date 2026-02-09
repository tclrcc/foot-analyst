package com.tony.sportsAnalytics.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchDetailStats {
    // Stats offensives
    private Double xG;              // Expected Goals du match
    private Integer shots;          // Tirs totaux
    private Integer shotsOnTarget;  // Tirs cadrés

    // Stats de contrôle
    private Integer possession;     // % Possession (ex: 55)
    private Integer corners;

    // Stats défensives/Discipline
    private Integer fouls;          // Fautes
    private Integer yellowCards;
    private Integer redCards;
}
