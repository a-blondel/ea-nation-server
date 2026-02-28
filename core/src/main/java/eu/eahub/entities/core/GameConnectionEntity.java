package eu.eahub.entities.core;

import eu.eahub.entities.stats.MohhGameReportEntity;
import eu.eahub.entities.stats.NfsGameReportEntity;
import eu.eahub.entities.stats.NhlGameReportEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "GAME_CONNECTION", schema = "core")
public class GameConnectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "GAME_ID", nullable = false)
    private GameEntity game;

    @Column(name = "IS_HOST", nullable = false)
    private boolean isHost;

    @ManyToOne
    @JoinColumn(name = "PERSONA_CONNECTION_ID", nullable = false)
    private PersonaConnectionEntity personaConnection;

    @Column(name = "START_TIME", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    @OneToOne(mappedBy = "gameConnection", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private MohhGameReportEntity mohhGameReport;

    @OneToOne(mappedBy = "gameConnection", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NfsGameReportEntity nfsGameReport;

    @OneToOne(mappedBy = "gameConnection", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NhlGameReportEntity nhlGameReport;
}
