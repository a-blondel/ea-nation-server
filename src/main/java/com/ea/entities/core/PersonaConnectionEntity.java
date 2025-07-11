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
@Table(name = "PERSONA_CONNECTION", schema = "core")
public class PersonaConnectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "PERSONA_ID", nullable = false)
    private PersonaEntity persona;

    private String address;

    private String vers;

    private String slus;

    private boolean isHost;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @OneToMany(mappedBy = "personaConnection")
    private Set<GameConnectionEntity> gameConnections;

}
