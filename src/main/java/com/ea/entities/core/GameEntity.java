package com.ea.entities.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "GAME", schema = "core")
public class GameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long originalId;

    private String vers;

    private String name;

    private String params;

    private String sysflags;

    private String pass;

    private int minsize;

    private int maxsize;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private boolean isStarted;

    private Integer roomId;

    @OneToMany(mappedBy = "game", fetch = FetchType.EAGER)
    private Set<GameConnectionEntity> gameConnections;

}
