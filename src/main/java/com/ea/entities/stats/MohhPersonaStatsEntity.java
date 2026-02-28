package com.ea.entities.stats;

import com.ea.entities.core.PersonaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MOHH_PERSONA_STATS", schema = "stats")
public class MohhPersonaStatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "PERSONA_ID", nullable = false)
    private PersonaEntity persona;

    private String vers;

    @Column(name = "KILL")
    private int kill;

    @Column(name = "DEATH")
    private int death;

    @Column(name = "SHOT")
    private int shot;

    @Column(name = "HIT")
    private int hit;

    @Column(name = "HEAD")
    private int head;

    @Column(name = "COLTSHOT")
    private int coltShot;

    @Column(name = "COLTHIT")
    private int coltHit;

    @Column(name = "COLTKILL")
    private int coltKill;

    @Column(name = "COLTHEAD")
    private int coltHead;

    @Column(name = "TOMSHOT")
    private int tomShot;

    @Column(name = "TOMHIT")
    private int tomHit;

    @Column(name = "TOMKILL")
    private int tomKill;

    @Column(name = "TOMHEAD")
    private int tomHead;

    @Column(name = "BARSHOT")
    private int barShot;

    @Column(name = "BARHIT")
    private int barHit;

    @Column(name = "BARKILL")
    private int barKill;

    @Column(name = "BARHEAD")
    private int barHead;

    @Column(name = "GARSHOT")
    private int garShot;

    @Column(name = "GARHIT")
    private int garHit;

    @Column(name = "GARKILL")
    private int garKill;

    @Column(name = "GARHEAD")
    private int garHead;

    @Column(name = "ENFIELDSHOT")
    private int enfieldShot;

    @Column(name = "ENFIELDHIT")
    private int enfieldHit;

    @Column(name = "ENFIELDKILL")
    private int enfieldKill;

    @Column(name = "ENFIELDHEAD")
    private int enfieldHead;

    @Column(name = "SHOTTYSHOT")
    private int shottyShot;

    @Column(name = "SHOTTYHIT")
    private int shottyHit;

    @Column(name = "SHOTTYKILL")
    private int shottyKill;

    @Column(name = "SHOTTYHEAD")
    private int shottyHead;

    @Column(name = "BAZSHOT")
    private int bazShot;

    @Column(name = "BAZHIT")
    private int bazHit;

    @Column(name = "BAZKILL")
    private int bazKill;

    @Column(name = "BAZHEAD")
    private int bazHead;

    @Column(name = "LUGERSHOT")
    private int lugerShot;

    @Column(name = "LUGERHIT")
    private int lugerHit;

    @Column(name = "LUGERKILL")
    private int lugerKill;

    @Column(name = "LUGERHEAD")
    private int lugerHead;

    @Column(name = "MP40SHOT")
    private int mp40Shot;

    @Column(name = "MP40HIT")
    private int mp40Hit;

    @Column(name = "MP40KILL")
    private int mp40Kill;

    @Column(name = "MP40HEAD")
    private int mp40Head;

    @Column(name = "MP44SHOT")
    private int mp44Shot;

    @Column(name = "MP44HIT")
    private int mp44Hit;

    @Column(name = "MP44KILL")
    private int mp44Kill;

    @Column(name = "MP44HEAD")
    private int mp44Head;

    @Column(name = "KARSHOT")
    private int karShot;

    @Column(name = "KARHIT")
    private int karHit;

    @Column(name = "KARKILL")
    private int karKill;

    @Column(name = "KARHEAD")
    private int karHead;

    @Column(name = "GEWRSHOT")
    private int gewrShot;

    @Column(name = "GEWRHIT")
    private int gewrHit;

    @Column(name = "GEWRKILL")
    private int gewrKill;

    @Column(name = "GEWRHEAD")
    private int gewrHead;

    @Column(name = "PANZSHOT")
    private int panzShot;

    @Column(name = "PANZHIT")
    private int panzHit;

    @Column(name = "PANZKILL")
    private int panzKill;

    @Column(name = "PANZHEAD")
    private int panzHead;

    @Column(name = "GRENTHROW")
    private int grenThrow;

    @Column(name = "GRENKILL")
    private int grenKill;

    @Column(name = "MELEEKILL")
    private int meleeKill;

    @Column(name = "WIN")
    private int win;

    @Column(name = "LOSS")
    private int loss;

    @Column(name = "AXIS")
    private int axis;

    @Column(name = "ALLIES")
    private int allies;

    @Column(name = "DMRND")
    private int dmRnd;

    @Column(name = "CTFAXIS")
    private int ctfAxis;

    @Column(name = "CTFALLIES")
    private int ctfAllies;

    @Column(name = "DEMAXIS")
    private int demAxis;

    @Column(name = "DEMALLIES")
    private int demAllies;

    @Column(name = "CAPAXIS")
    private int capAxis;

    @Column(name = "CAPALLIES")
    private int capAllies;

    @Column(name = "KOHAXIS")
    private int kohAxis;

    @Column(name = "KOHALLIES")
    private int kohAllies;

    @Column(name = "BLAXIS")
    private int blAxis;

    @Column(name = "BLALLIES")
    private int blAllies;

    @Column(name = "TDMAXIS")
    private int tdmAxis;

    @Column(name = "TDMALLIES")
    private int tdmAllies;

    @Column(name = "MAP1")
    private int map1;

    @Column(name = "MAP2")
    private int map2;

    @Column(name = "MAP3")
    private int map3;

    @Column(name = "MAP4")
    private int map4;

    @Column(name = "MAP5")
    private int map5;

    @Column(name = "MAP6")
    private int map6;

    @Column(name = "MAP7")
    private int map7;

    @Column(name = "MAP8")
    private int map8;

    @Column(name = "MAP9")
    private int map9;

    @Column(name = "MAP10")
    private int map10;

    @Column(name = "MAP11")
    private int map11;

    @Column(name = "MAP12")
    private int map12;

    @Column(name = "MAP13")
    private int map13;

    @Column(name = "MAP14")
    private int map14;

    @Column(name = "MAP15")
    private int map15;

    @Column(name = "MAP16")
    private int map16;

    @Column(name = "MAP17")
    private int map17;

    @Column(name = "MAP18")
    private int map18;

    @Column(name = "MAP19")
    private int map19;

    @Column(name = "MAP20")
    private int map20;

    @Column(name = "MAP21")
    private int map21;

    @Column(name = "MAP22")
    private int map22;

    @Column(name = "MAP23")
    private int map23;

    @Column(name = "MAP24")
    private int map24;

    @Column(name = "MAP25")
    private int map25;

    @Column(name = "MAP26")
    private int map26;

    @Column(name = "MAP27")
    private int map27;

    @Column(name = "MAP28")
    private int map28;

    @Column(name = "PLAYTIME")
    private int playTime;
}
