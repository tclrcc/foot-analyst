package com.tony.sportsAnalytics.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "match_analysis")
@Data
public class MatchAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le nom de l'équipe domicile est requis")
    private String homeTeamName;

    @NotBlank(message = "Le nom de l'équipe extérieur est requis")
    private String awayTeamName;

    @FutureOrPresent(message = "La date du match doit être présente ou future")
    private LocalDateTime matchDate;

    // @Embedded permet d'aplatir les colonnes dans la table match_analysis
    // On surcharge les noms de colonnes pour éviter les collisions
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "rank", column = @Column(name = "home_rank")),
            @AttributeOverride(name = "points", column = @Column(name = "home_points")),
            @AttributeOverride(name = "goalsFor", column = @Column(name = "home_goals_for")),
            @AttributeOverride(name = "goalsAgainst", column = @Column(name = "home_goals_against")),
            @AttributeOverride(name = "xG", column = @Column(name = "home_xg")),
            @AttributeOverride(name = "last5MatchesPoints", column = @Column(name = "home_form_l5"))
    })
    @Valid // Active la validation imbriquée
    private TeamStats homeStats;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "rank", column = @Column(name = "away_rank")),
            @AttributeOverride(name = "points", column = @Column(name = "away_points")),
            @AttributeOverride(name = "goalsFor", column = @Column(name = "away_goals_for")),
            @AttributeOverride(name = "goalsAgainst", column = @Column(name = "away_goals_against")),
            @AttributeOverride(name = "xG", column = @Column(name = "away_xg")),
            @AttributeOverride(name = "last5MatchesPoints", column = @Column(name = "away_form_l5"))
    })
    @Valid
    private TeamStats awayStats;

    // Pourra servir plus tard pour stocker ton analyse perso
    @Column(columnDefinition = "TEXT")
    private String myNotes;

    @Embedded
    private PredictionResult prediction;
}
