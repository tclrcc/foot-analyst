package com.tony.sportsAnalytics.model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamStats {

    @NotNull(message = "Le classement est obligatoire") @Min(1) private Integer rank;

    @Min(0) private Integer points;

    private Integer goalsFor;
    private Integer goalsAgainst;

    // xG (Expected Goals) - Utilisation de Double pour la précision
    private Double xG;

    // Forme récente (ex: points sur les 5 derniers matchs)
    private Integer last5MatchesPoints;
}
