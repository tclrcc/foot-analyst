package com.tony.sportsAnalytics.model.dto;

import com.tony.sportsAnalytics.model.TeamStats;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MatchAnalysisRequest {
    @NotNull private Long homeTeamId;
    @NotNull private Long awayTeamId;
    @NotNull private LocalDateTime matchDate;

    // --- NOUVEAU : Cotes Bookmakers ---
    private Double odds1;
    private Double oddsN;
    private Double odds2;

    // --- NOUVEAU : Contexte ---
    private MatchContext context; // Créer une petite classe interne ou dédiée

    private TeamStats homeStats;
    private TeamStats awayStats;

    private Integer homeScore;
    private Integer awayScore;

    @Data
    public static class MatchContext {
        private boolean homeKeyPlayerMissing;
        private boolean awayKeyPlayerMissing;
        private boolean homeTired;
        private boolean awayNewCoach;
    }
}
