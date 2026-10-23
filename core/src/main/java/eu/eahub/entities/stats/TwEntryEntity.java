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
@Table(name = "TW_ENTRY", schema = "stats")
public class TwEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String token;

    @ManyToOne
    @JoinColumn(name = "EVENT_ID", nullable = false)
    private TwEventEntity event;

    @ManyToOne
    @JoinColumn(name = "PERSONA_ID", nullable = false)
    private PersonaEntity persona;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private boolean counted;

    private Integer rnk;

    private Integer done;

    private Integer quit;

    private Integer type;

    private Integer score;

    private Integer club;

    private Integer strokes;

    private Integer putts;

    private Integer holes;

    private Integer eags;

    private Integer bird;

    private Integer aces;

    private Integer gir;

    private Integer lput;

    private Integer drvs;

    private Integer frwy;

    private Integer ldrv;

    private Integer shtc;

    private Integer pars;

    private Integer sbog;

    private Integer dbog;

    private Integer tbog;

    private Integer comp;

    private Integer finalRank;

    private Long prize;
}
