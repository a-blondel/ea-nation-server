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
@Table(name = "FIFA_PERSONA_STATS", schema = "stats")
public class FifaPersonaStatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "PERSONA_ID", nullable = false)
    private PersonaEntity persona;

    private String vers;
    private String slus;

    private int wins;           // total wins
    private int losses;         // total losses
    private int draw;           // total draws
    private int score;          // total goals scored
    private int scoreAgainst;   // total goals conceded
    private int sht;            // total shots
    private int pasm;           // total passes missed
    private int pass;           // total passes completed
    private int cor;            // total corners
    private int off;            // total offsides
    private int pos;            // total possession
    private int tcm;            // total tackles missed
    private int tcs;            // total tackles successful
    private int fls;            // total fouls
    private int ylw;            // total yellow cards
    private int red;            // total red cards
    private int time;           // total ingame time
    private int skil;           // total skill
    private int disc;           // total disconnections
    private int quit;           // total quits
    private int cheat;          // total cheats
    private int weight;         // total weight
    private int home;           // total home games
    private int away;           // total away games
}
