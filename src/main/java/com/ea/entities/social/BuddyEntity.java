package com.ea.entities.social;

import com.ea.entities.core.PersonaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "BUDDY", schema = "social")
public class BuddyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "FROM_PERSONA_ID", nullable = false)
    private PersonaEntity fromPersona;

    @ManyToOne
    @JoinColumn(name = "TO_PERSONA_ID", nullable = false)
    private PersonaEntity toPersona;

    @Column(name = "LIST", length = 1, nullable = false)
    private String list;

    @Column(name = "STATUS", length = 1, nullable = false)
    private String status;
}
