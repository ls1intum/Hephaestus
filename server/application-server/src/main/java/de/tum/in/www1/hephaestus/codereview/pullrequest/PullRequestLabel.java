package de.tum.in.www1.hephaestus.codereview.pullrequest;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class PullRequestLabel {
    private String name;
    private String color;
}
