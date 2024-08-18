package de.tum.in.www1.hephaestus.codereview.repository;

import java.time.Instant;
import java.util.List;

import de.tum.in.www1.hephaestus.codereview.pullrequest.Pullrequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table
@Getter
@Setter
public class Repository {

    /**
     * Unique identifier for a Repository
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long uid;

    @NotNull
    private String id;

    @NotNull
    private String name;

    @NotNull
    private String nameWithOwner;

    @NotNull
    private String description;

    @NotNull
    private String url;

    @OneToMany(mappedBy = "repository", fetch = FetchType.LAZY)
    private List<Pullrequest> pullRequests;

    @NotNull
    private Instant addedAt;
}
