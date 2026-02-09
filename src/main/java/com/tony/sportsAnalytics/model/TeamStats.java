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

    // --- STATS GLOBALES (SAISON) ---
    private Integer matchesPlayed;
    private Integer matchesPlayedHome;
    private Integer matchesPlayedAway;

    private Integer goalsFor;
    private Integer goalsAgainst;

    // --- STATS FORME (5 DERNIERS MATCHS) - ESSENTIEL POUR POINT 4 ---
    private Integer last5MatchesPoints; // Ex: 13 (V-V-N-V-V)

    // Nouveaux champs pour la précision de l'attaque/défense récente
    private Integer goalsForLast5;      // Buts marqués sur les 5 derniers
    private Integer goalsAgainstLast5;  // Buts encaissés sur les 5 derniers

    private Double xG; // Expected Goals (Global ou Moyen)

    // --- STATS CONTEXTUELLES ---
    private Integer venuePoints;   // Points à Dom/Ext
    private Integer venueMatches;  // MJ à Dom/Ext
}
