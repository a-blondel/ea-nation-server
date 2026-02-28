package eu.eahub.entities.core;

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
@Table(name = "USER_SET", schema = "core")
public class UserSetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String vers;

    private String name;

    private String description;

    private String params;

    private String sysflags;

    private String custflags;

    private int size;

    private int type;

    private int updates;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id")
    private PersonaEntity owner;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @OneToMany(mappedBy = "userSet", fetch = FetchType.EAGER)
    private Set<UserSetMemberEntity> members;

}

