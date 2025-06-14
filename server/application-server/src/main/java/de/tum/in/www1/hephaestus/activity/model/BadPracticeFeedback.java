package de.tum.in.www1.hephaestus.activity.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
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
    @Lob
    private String explanation;

    private OffsetDateTime creationTime;
}
