package de.tum.in.www1.hephaestus.gitprovider.label;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;

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

    // Ignored GitHub properties:
    // - default
}
