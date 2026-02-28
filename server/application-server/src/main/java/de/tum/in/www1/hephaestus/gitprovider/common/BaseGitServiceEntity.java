package de.tum.in.www1.hephaestus.gitprovider.common;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;

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

    @Column(nullable = false, length = 10)
    @ColumnDefault("'GITHUB'")
    @Enumerated(EnumType.STRING)
    protected GitProviderType provider = GitProviderType.GITHUB;
}
