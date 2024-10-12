package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class PullRequestLabel {
    @NonNull
    private String name;

    @NonNull
    private String color;
}
