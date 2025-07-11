package com.ea.entities.social;

import com.ea.entities.core.PersonaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MESSAGE", schema = "social")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "FROM_PERSONA_ID", nullable = false)
    private PersonaEntity fromPersona;

    @ManyToOne
    @JoinColumn(name = "TO_PERSONA_ID", nullable = false)
    private PersonaEntity toPersona;

    @Column(name = "BODY", length = 255, nullable = false)
    private String body;

    @Column(name = "ACK", nullable = false)
    private Boolean ack = false;

    @Column(name = "CREATED_ON", nullable = false)
    private LocalDateTime createdOn;
}
