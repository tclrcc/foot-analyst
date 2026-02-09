package com.tony.sportsAnalytics.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
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

    @ManyToOne
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    private LocalDateTime matchDate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "rank", column = @Column(name = "home_rank")),
            @AttributeOverride(name = "points", column = @Column(name = "home_points")),
            @AttributeOverride(name = "goalsFor", column = @Column(name = "home_goals_for")),
            @AttributeOverride(name = "goalsAgainst", column = @Column(name = "home_goals_against")),
            @AttributeOverride(name = "xG", column = @Column(name = "home_xg")),
            @AttributeOverride(name = "last5MatchesPoints", column = @Column(name = "home_form_l5")),
            @AttributeOverride(name = "venuePoints", column = @Column(name = "home_venue_points")),
            @AttributeOverride(name = "venueMatches", column = @Column(name = "home_venue_matches"))
    })
    @Valid
    private TeamStats homeStats;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "rank", column = @Column(name = "away_rank")),
            @AttributeOverride(name = "points", column = @Column(name = "away_points")),
            @AttributeOverride(name = "goalsFor", column = @Column(name = "away_goals_for")),
            @AttributeOverride(name = "goalsAgainst", column = @Column(name = "away_goals_against")),
            @AttributeOverride(name = "xG", column = @Column(name = "away_xg")),
            @AttributeOverride(name = "last5MatchesPoints", column = @Column(name = "away_form_l5")),
            @AttributeOverride(name = "venuePoints", column = @Column(name = "away_venue_points")),
            @AttributeOverride(name = "venueMatches", column = @Column(name = "away_venue_matches"))
    })
    @Valid
    private TeamStats awayStats;

    private Integer homeScore;
    private Integer awayScore;

    // Pourra servir plus tard pour stocker ton analyse perso
    @Column(columnDefinition = "TEXT")
    private String myNotes;

    @Embedded
    private PredictionResult prediction;

    @Transient
    private String leagueNameInput;
    @Transient
    private String homeTeamNameInput;
    @Transient
    private String awayTeamNameInput;
}
