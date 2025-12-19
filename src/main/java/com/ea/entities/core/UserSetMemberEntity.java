package com.ea.entities.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * UserSetMember entity for tracking players in a UserSet.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "USER_SET_MEMBER", schema = "core")
public class UserSetMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_set_id")
    private UserSetEntity userSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id")
    private PersonaEntity persona;

    @Column(name = "slot")
    private int slot;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

}

