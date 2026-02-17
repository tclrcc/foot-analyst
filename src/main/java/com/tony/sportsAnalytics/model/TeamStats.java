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
public class TeamStats {

    private Integer rank;
    private Integer rankHome;
    private Integer rankAway;

    private Integer points;
    private Integer pointsHome;
    private Integer pointsAway;

    // Stats Globales
    private Integer wins = 0;   // V
    private Integer draws = 0;  // N
    private Integer losses = 0; // D
    private Integer goalsFor = 0;
    private Integer goalsAgainst = 0;

    // Stats Domicile
    private Integer winsHome = 0;
    private Integer drawsHome = 0;
    private Integer lossesHome = 0;
    private Integer goalsForHome = 0;
    private Integer goalsAgainstHome = 0;

    // Stats Extérieur
    private Integer winsAway = 0;
    private Integer drawsAway = 0;
    private Integer lossesAway = 0;
    private Integer goalsForAway = 0;
    private Integer goalsAgainstAway = 0;

    // --- STATS GLOBALES (SAISON) ---
    private Integer matchesPlayed;
    private Integer matchesPlayedHome;
    private Integer matchesPlayedAway;

    // --- STATS FORME (5 DERNIERS MATCHS) - ESSENTIEL POUR POINT 4 ---
    private Integer last5MatchesPoints; // Ex: 13 (V-V-N-V-V)

    // Nouveaux champs pour la précision de l'attaque/défense récente
    private Integer goalsForLast5;      // Buts marqués sur les 5 derniers
    private Integer goalsAgainstLast5;  // Buts encaissés sur les 5 derniers

    @JsonProperty("xG")
    private Double xG; // Expected Goals (Global ou Moyen)
    private Double xGA;

    private Double ppda;      // Pressing intensity
    private Double fieldTilt;  // Territorial dominance (%)
    private Double deepEntries; // Entrées dans la surface par match

    // --- STATS CONTEXTUELLES ---
    private Integer venuePoints;   // Points à Dom/Ext
    private Integer venueMatches;  // MJ à Dom/Ext

    // --- MOYENNES STATISTIQUES (SAISON EN COURS) ---
    private Double avgShots;           // Tirs par match
    private Double avgShotsOnTarget;   // Tirs cadrés par match
    private Double avgPossession;      // Possession moyenne (%)
    private Double avgCorners;         // Corners obtenus par match
    private Double avgCrosses;         // Centres tentés par match
    private Double avgProgressivePasses;
}
