package eu.eahub.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "param", schema = "discord")
public class ParamEntity {

    @Id
    private String paramKey;

    private String paramValue;
}
