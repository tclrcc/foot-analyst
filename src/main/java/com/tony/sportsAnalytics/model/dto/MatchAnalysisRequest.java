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

    // Stats manuelles
    private TeamStats homeStats;
    private TeamStats awayStats;

    // Résultats (optionnel, pour l'historique)
    private Integer homeScore;
    private Integer awayScore;
}
