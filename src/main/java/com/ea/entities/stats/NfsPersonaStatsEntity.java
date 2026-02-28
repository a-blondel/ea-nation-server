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
@Table(name = "NFS_PERSONA_STATS", schema = "stats")
public class NfsPersonaStatsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "persona_id", nullable = false)
    private PersonaEntity persona;

    private String vers;
    private int wins;
    private int losses;
    private int skil;
    private int time;
    private int racetime;
    private int lapscomp;
    private int didcheat;
    private int didquit;
    private int diddisc;
}
