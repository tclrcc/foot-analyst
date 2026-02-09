package com.tony.sportsAnalytics.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamStats {

    private Integer rank;
    private Integer points;

    // --- NOUVEAU V7 : MATCHS JOUÉS ---
    private Integer matchesPlayed;      // Total
    private Integer matchesPlayedHome;  // Domicile
    private Integer matchesPlayedAway;  // Extérieur

    private Integer goalsFor;
    private Integer goalsAgainst;

    private Double xG;
    private Integer last5MatchesPoints;

    // Stats contextuelles (Points pris à Dom/Ext)
    private Integer venuePoints;
}
