package de.tum.in.www1.hephaestus.codereview.repository;

import java.time.Instant;

import de.tum.in.www1.hephaestus.codereview.pullrequest.PullrequestConnection;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
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
    private Long id;

    @Column(name = "github_id")
    private String githubId;

    @Column
    private String name;

    @Column(name = "name_with_owner")
    private String nameWithOwner;

    @Column
    private String description;

    @Column
    private String url;

    @OneToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "connection_id", referencedColumnName = "id")
    private PullrequestConnection pullRequests;

    @Column(name = "added_at")
    private Instant addedAt;

    public String toString() {
        return "Repository [id=" + id + ", name=" + name + ", nameWithOwner=" + nameWithOwner + ", description="
                + description + ", url=" + url + ", pullRequests=" + pullRequests + ", addedAt=" + addedAt + "]";
    }
}
