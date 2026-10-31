package eu.eahub.entities.stats;

import eu.eahub.entities.core.PersonaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TW_PERSONA_STATS", schema = "stats")
public class TwPersonaStatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "PERSONA_ID", nullable = false)
    private PersonaEntity persona;

    private String vers;

    private int matchPoints;

    private int matchWins;

    private int matchLosses;

    private int matchTies;

    private int matchStreak;

    private int matchComplete;

    private int matchIncomplete;

    private int strokePoints;

    private int strokeWins;

    private int strokeLosses;

    private int strokeTies;

    private int strokeStreak;

    private int strokeComplete;

    private int strokeIncomplete;

    private int holes;

    private int putts;

    private int puttHoles;

    private int gir;

    private int girHoles;

    private int fairways;

    private int drives;

    private int eagles;

    private int birdies;

    private int aces;

    private int longestPutt;

    private int longestDrive;

    private int rounds;

    private int roundStrokes;

    private int roundBirdies;

    private Integer bestRound;

    private int events;

    private int eventWins;

    @Column(name = "TOP10")
    private int top10;

    @Column(name = "TOP25")
    private int top25;

    private long earnings;
}
