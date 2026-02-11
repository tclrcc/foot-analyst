package com.tony.sportsAnalytics.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class MatchEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private MatchAnalysis match;

    @ManyToOne
    private Player player; // Le buteur ou le joueur sanctionné

    @ManyToOne
    private Player assistPlayer; // Le passeur (optionnel)

    private Integer minute;

    @Enumerated(EnumType.STRING)
    private EventType type; // GOAL, CARD_YELLOW, CARD_RED, SUBSTITUTION

    public enum EventType { GOAL, OWN_GOAL, YELLOW_CARD, RED_CARD }
}
