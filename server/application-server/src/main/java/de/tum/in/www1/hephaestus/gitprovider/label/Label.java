package de.tum.in.www1.hephaestus.gitprovider.label;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "label")
@Getter
@Setter
@NoArgsConstructor
public class Label {

    @Id
    protected Long id;

    // Note: This entity does not have a createdAt and updatedAt field

    @NonNull
    private String name;

    private String description;

    // 6-character hex code, without the leading #, identifying the color
    @NonNull
    private String color;

    @ManyToMany(mappedBy = "labels")
    @ToString.Exclude
    private Set<Issue> issues = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "repository_id")
    @ToString.Exclude
    private Repository repository;

    @ManyToMany(mappedBy = "labels")
    @ToString.Exclude
    private Set<Team> teams = new HashSet<>();

    public void removeAllTeams() {
        this.teams.forEach(team -> team.getLabels().remove(this));
        this.teams.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Label label = (Label) o;
        return id != null && Objects.equals(id, label.id);
    }

    @Override
    public int hashCode() {
        // Use a constant hashCode to ensure consistency across entity state transitions
        // (transient -> managed -> detached). The ID may be null before persistence.
        return getClass().hashCode();
    }
    // Ignored GitHub properties:
    // - default
}
