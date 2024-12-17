package de.tum.in.www1.hephaestus.gitprovider.common;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
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
public abstract class BaseGitServiceEntity {

    @Id
    protected Long id;

    protected OffsetDateTime createdAt;

    protected OffsetDateTime updatedAt;
}
