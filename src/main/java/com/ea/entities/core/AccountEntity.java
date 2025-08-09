package com.ea.entities.core;

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
@Table(name = "ACCOUNT", schema = "core")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String pass;

    private String mail;

    private String loc;

    private String born;

    private String zip;

    private String gend;

    private LocalDateTime createdOn;

    private LocalDateTime updatedOn;

    private boolean isBanned;

    @OneToMany(mappedBy = "account", fetch = FetchType.EAGER)
    @OrderBy("id DESC")
    private Set<PersonaEntity> personas;

}
