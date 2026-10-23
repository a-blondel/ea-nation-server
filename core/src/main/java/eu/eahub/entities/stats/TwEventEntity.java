package eu.eahub.entities.stats;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TW_EVENT", schema = "stats")
public class TwEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String vers;

    private int eventDay;

    private String name;

    private int course;

    private long purse;

    private int conditions;

    private Integer seriesStart;

    private LocalDateTime settledOn;
}
