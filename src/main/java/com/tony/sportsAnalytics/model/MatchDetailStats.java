package com.tony.sportsAnalytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchDetailStats {
    // --- OFFENSIF ---
    @JsonProperty("xG")   // <--- FORCE LE NOM JSON EXACT
    private Double xG;

    @JsonProperty("xGOT") // <--- FORCE LE NOM JSON EXACT
    private Double xGOT;
    private Integer shots;          // Tirs totaux
    private Integer shotsOnTarget;  // Tirs cadrés
    private Integer bigChances;     // Grosses occasions ratées/créées

    // --- CONSTRUCTION ---
    private Integer possession;     // %
    private Integer passesTotal;    // Nombre de passes tentées
    private Integer passesCompleted;// Nombre de passes réussies
    private Integer crosses;        // Centres
    private Integer corners;

    // --- DEFENSIF / DISCIPLINE ---
    private Integer fouls;
    private Integer yellowCards;
    private Integer redCards;

    // Helper pour calculer le % de passes
    public double getPassAccuracy() {
        if (passesTotal == null || passesTotal == 0) return 0.0;
        return (double) passesCompleted / passesTotal;
    }
}
