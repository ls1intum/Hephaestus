package de.tum.in.www1.hephaestus.codereview.pullrequest;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestLabel {
    @NonNull
    private String name;

    @NonNull
    private String color;
}
