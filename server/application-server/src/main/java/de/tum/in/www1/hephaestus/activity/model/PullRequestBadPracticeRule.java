package de.tum.in.www1.hephaestus.activity.model;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Setter
@Getter
@NoArgsConstructor
@ToString
public class PullRequestBadPracticeRule {

   @Id
   @GeneratedValue
   private Long id;

   private String title;

   private String description;

   private String conditions;

   @ManyToOne
   @JoinColumn(name = "repository_id")
   private Repository repository;

    private boolean active;
}
