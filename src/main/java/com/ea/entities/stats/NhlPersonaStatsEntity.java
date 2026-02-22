package com.ea.entities.stats;

import com.ea.entities.core.PersonaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "NHL_PERSONA_STATS", schema = "stats")
public class NhlPersonaStatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "PERSONA_ID", nullable = false)
    private PersonaEntity persona;

    private String vers;

    private int points;

    private int wins;

    private int otWins;

    private int losses;

    private int otLosses;

    private int streak;

    private int draw;

    private int score;

    private int scoreAgainst;

    private int hits;

    private int shots;

    private int penmin;

    private int ppg;

    private int ppo;

    private int shg;

    private int home;

    private int away;

    private int disc;

    private int quit;

    private int cheat;

    private int weight;

    private int skil;

    private int time;

}