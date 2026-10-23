package eu.eahub.entities.stats;

import eu.eahub.entities.core.GameConnectionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "TW_GAME_REPORT", schema = "stats")
public class TwGameReportEntity {

    @Id
    private Long gameConnectionId;

    @OneToOne
    @JoinColumn(name = "GAME_CONNECTION_ID", nullable = false)
    @MapsId
    private GameConnectionEntity gameConnection;

    private int side;

    private int rnk;

    private int done;

    private int quit;

    private int type;

    private int score;

    private int club;

    private int strokes;

    private int putts;

    private int holes;

    private int eags;

    private int bird;

    private int aces;

    private int gir;

    private int lput;

    private int drvs;

    private int frwy;

    private int ldrv;

    private int shtc;

    private int pars;

    private int sbog;

    private int dbog;

    private int tbog;

    private int comp;
}
