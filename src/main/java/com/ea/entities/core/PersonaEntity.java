package com.ea.entities.core;

import com.ea.entities.social.BuddyEntity;
import com.ea.entities.social.FeedbackEntity;
import com.ea.entities.social.MessageEntity;
import com.ea.entities.stats.MohhPersonaStatsEntity;
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

    @OneToMany(mappedBy = "persona", fetch = FetchType.EAGER)
    private Set<PersonaConnectionEntity> personaConnections;

    @OneToMany(mappedBy = "fromPersona", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<BuddyEntity> buddiesFrom;

    @OneToMany(mappedBy = "toPersona", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<BuddyEntity> buddiesTo;

    @OneToMany(mappedBy = "fromPersona", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<MessageEntity> messagesSent;

    @OneToMany(mappedBy = "toPersona", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<MessageEntity> messagesReceived;

    @OneToMany(mappedBy = "fromPersona", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<FeedbackEntity> feedbackGiven;

    @OneToMany(mappedBy = "toPersona", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<FeedbackEntity> feedbackReceived;

}
