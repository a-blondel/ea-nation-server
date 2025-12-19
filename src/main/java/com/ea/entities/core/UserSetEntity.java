package com.ea.entities.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * UserSet entity for NFS Most Wanted PC/PS2 pre-race lobbies.
 * A UserSet is a group of players who can chat and configure their settings before joining a game.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "USER_SET", schema = "core")
public class UserSetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String vers;

    private String name;

    @Column(name = "description")
    private String description;

    private String params;

    private String sysflags;

    private String custflags;

    private int size;

    @Column(name = "type")
    private int type;

    @Column(name = "updates")
    private int updates;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private PersonaEntity owner;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @OneToMany(mappedBy = "userSet", fetch = FetchType.EAGER)
    private Set<UserSetMemberEntity> members;

}

