package eu.eahub.entities.core;

import eu.eahub.entities.stats.MohhPersonaStatsEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "PERSONA", schema = "core")
public class PersonaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    private AccountEntity account;

    private String pers;

    private int rp;

    private LocalDateTime createdOn;

    private LocalDateTime deletedOn;

    @OneToMany(mappedBy = "persona", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<MohhPersonaStatsEntity> mohhPersonaStats;

    @OneToMany(mappedBy = "persona", fetch = FetchType.LAZY)
    private Set<PersonaConnectionEntity> personaConnections;

}
