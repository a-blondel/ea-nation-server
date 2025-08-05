package com.ea.entities.stats;

import com.ea.entities.core.GameConnectionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "NHL_GAME_REPORT", schema = "stats")
public class NhlGameReportEntity {

    @Id
    private Long gameConnectionId;

    @OneToOne
    @JoinColumn(name = "GAME_CONNECTION_ID", nullable = false)
    @MapsId
    private GameConnectionEntity gameConnection;

    private int rnk;

    private int score;

    private int dscore;

    private int hits;

    private int shots;

    private int penmin;

    private int ppg;

    private int ppo;

    private int shg;

    private int team;

    private int home;

    private int disc;

    private int quit;

    private int cheat;

    private int weight;

    private int skil;

    private int tid;

    private int tmid;

    private int venue;

    private int type;

    private int pnum;

    private int plen;

    private int ot;

    private int time;

    private int dtime;

}
