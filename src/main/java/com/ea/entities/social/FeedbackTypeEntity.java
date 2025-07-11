package com.ea.entities.social;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "FEEDBACK_TYPE", schema = "social")
public class FeedbackTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "LABEL", length = 32, nullable = false)
    private String label;

    @Column(name = "NUMBER", nullable = false)
    private BigDecimal number;

    @OneToMany(mappedBy = "feedbackType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<FeedbackEntity> feedbacks;

}
