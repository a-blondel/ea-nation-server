package eu.eahub.entities.stats;

import eu.eahub.entities.core.PersonaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TW_GOLFER", schema = "stats")
public class TwGolferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "PERSONA_ID", nullable = false)
    private PersonaEntity persona;

    private String vers;

    private String data;

    private LocalDateTime updatedOn;
}
