package de.tum.in.www1.hephaestus.codereview.repository;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import de.tum.in.www1.hephaestus.codereview.pullrequest.Pullrequest;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "repository")
@Getter
@Setter
@NoArgsConstructor
public class Repository {

    /**
     * Unique identifier for a Repository
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id")
    private Long githubId;

    @Column
    private String name;

    @Column(name = "name_with_owner")
    private String nameWithOwner;

    @Column
    private String description;

    @Column
    private String url;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "repository", fetch = FetchType.EAGER)
    private Set<Pullrequest> pullRequests = new HashSet<>();;

    @Column(name = "added_at")
    private Instant addedAt;

    public String toString() {
        return "Repository{id=" + id + ", name=" + name + ", nameWithOwner=" + nameWithOwner + ", description="
                + description + ", url=" + url + ", #pullRequests=" + pullRequests.size() + ", addedAt=" + addedAt
                + "}";
    }
}
