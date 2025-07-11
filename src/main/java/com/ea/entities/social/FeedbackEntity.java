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
@Table(name = "FEEDBACK", schema = "social")
public class FeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "FROM_PERSONA_ID", nullable = false)
    private PersonaEntity fromPersona;

    @ManyToOne
    @JoinColumn(name = "TO_PERSONA_ID", nullable = false)
    private PersonaEntity toPersona;

    @Column(name = "VERS", length = 32, nullable = false)
    private String vers;

    @ManyToOne
    @JoinColumn(name = "TYPE_ID", nullable = false)
    private FeedbackTypeEntity feedbackType;

    @Column(name = "CREATED_ON", nullable = false)
    private LocalDateTime createdOn;
}
