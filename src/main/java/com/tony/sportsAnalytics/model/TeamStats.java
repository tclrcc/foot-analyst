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

    // --- NOUVEAU V7 : MATCHS JOUÉS (Stats Globales) ---
    private Integer matchesPlayed;      // Total
    private Integer matchesPlayedHome;  // Domicile
    private Integer matchesPlayedAway;  // Extérieur

    private Integer goalsFor;
    private Integer goalsAgainst;

    private Double xG;
    private Integer last5MatchesPoints;

    // --- STATS CONTEXTUELLES (Pour le calcul du match spécifique) ---
    // Ces champs sont remplis dynamiquement selon si l'équipe joue à Dom ou Ext
    private Integer venuePoints;   // Ex: Points à Domicile si c'est l'équipe Home

    // C'est ce champ qui manquait pour ton calcul :
    private Integer venueMatches;  // Ex: Nb Matchs à Domicile si c'est l'équipe Home
}
