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
@Table(name = "NFS_GAME_REPORT", schema = "stats")
public class NfsGameReportEntity {
    @Id
    private Long gameConnectionId;

    @OneToOne
    @JoinColumn(name = "GAME_CONNECTION_ID", nullable = false)
    @MapsId
    private GameConnectionEntity gameConnection;

    private Integer venue;
    private Integer gtyp;
    private Integer dir;
    private Integer numlaps;
    private Integer numplyrs;
    private Integer carrest;
    private Integer points;
    private Integer disc;
    private Integer skil;
    private Integer time;
    private Integer dtime;
    private Integer pos;
    private Integer car;
    private Integer racetime;
    private Integer lapscomp;
    private Integer lap;
    private Integer team;
    private Integer weight;
    private Integer dscore;
    private Integer home;
    private Integer didcheat;
    private Integer diddisc;
    private Integer didquit;
    private String auth;
    private Integer rnk;
}
