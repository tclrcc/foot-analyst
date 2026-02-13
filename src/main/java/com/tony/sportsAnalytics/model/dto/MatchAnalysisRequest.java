package com.tony.sportsAnalytics.model.dto;

import com.tony.sportsAnalytics.model.MatchDetailStats;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MatchAnalysisRequest {
    private Long homeTeamId;
    private Long awayTeamId;
    private LocalDateTime matchDate;
    private String season;

    // Cotes 1N2
    private Double odds1;
    private Double oddsN;
    private Double odds2;

    // --- AJOUT V15 : Cotes Marchés Secondaires ---
    private Double oddsOver15;
    private Double oddsOver25;
    private Double oddsBTTSYes;
    // ---------------------------------------------

    // Champs existants...
    private Integer homeScore;
    private Integer awayScore;
    private TeamStatsRequest homeStats;
    private TeamStatsRequest awayStats;
    private MatchContextRequest context;
    private MatchDetailStats homeMatchStats;
    private MatchDetailStats awayMatchStats;

    @Data
    public static class TeamStatsRequest {
        private Integer rank;
        private Integer points;
        private Double xG;
        private Double xGA; // Ajouté

        private Integer goalsFor;
        private Integer goalsAgainst;
        private Integer last5MatchesPoints;
        private Integer goalsForLast5;
        private Integer goalsAgainstLast5;
        private Integer matchesPlayed;
        private Integer matchesPlayedHome;
        private Integer matchesPlayedAway;
        private Integer venuePoints;

        // --- NOUVEAUX CHAMPS MOYENNES ---
        private Double avgShots;
        private Double avgShotsOnTarget;
        private Double avgPossession;
        private Double avgCorners;
        private Double avgCrosses;

        // --- STATS AVANCÉES ---
        private Double ppda;
        private Double fieldTilt;
        private Double deepEntries;
    }

    @Data
    public static class MatchContextRequest {
        private boolean homeKeyPlayerMissing;
        private boolean awayKeyPlayerMissing;
        private boolean homeTired;
        private boolean awayNewCoach;
    }
}
