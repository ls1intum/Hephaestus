package de.tum.in.www1.hephaestus.gitprovider.common;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class BaseGitServiceEntity {

    @Id
    @EqualsAndHashCode.Include
    protected Long id;

    protected Instant createdAt;

    protected Instant updatedAt;
}
