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
@Table(name = "FIFA_GAME_REPORT", schema = "stats")
public class FifaGameReportEntity {

    @Id
    private Long gameConnectionId;

    @OneToOne
    @JoinColumn(name = "GAME_CONNECTION_ID", nullable = false)
    @MapsId
    private GameConnectionEntity gameConnection;

    private int venue;      // venue/stadium id
    private int team;       // team selected
    private int pk;         // penalty kicks
    private int gld;        // ?
    private int sht;        // shots
    private int pasm;       // passes missed
    private int pass;       // passes completed
    private int cor;        // corners
    private int off;        // offsides
    private int pos;        // possession percentage
    private int tcm;        // tackles missed
    private int tcs;        // tackles successful
    private int fls;        // fouls
    private int ylw;        // yellow cards
    private int red;        // red cards
    private int time;       // ingame time
    private int type;       // game type
    private int skil;       // skill level
    private int plen;       // period length
    private int pnum;       // number of periods
    private int disc;       // disconnected (0/1)
    private int quit;       // quit game (0/1)
    private int cheat;      // cheated (0/1)
    private int dtime;      // ?
    private int score;      // final score
    private int weight;     // ?
    private int dscore;     // ?
    private int home;       // home or away (0/1)
    private int rnk;        // ranked or not
}
