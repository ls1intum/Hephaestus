package de.tum.in.www1.hephaestus.practices.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class BadPracticeFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @ManyToOne
    private PullRequestBadPractice pullRequestBadPractice;

    @NonNull
    String type;

    @NonNull
    @Column(columnDefinition = "TEXT")
    private String explanation;

    private Instant creationTime;
}
