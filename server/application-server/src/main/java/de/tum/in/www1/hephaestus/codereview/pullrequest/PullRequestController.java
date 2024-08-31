package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pullrequest")
public class PullRequestController {
    private final PullRequestService pullRequestService;

    public PullRequestController(PullRequestService pullRequestService) {
        this.pullRequestService = pullRequestService;
    }

    @GetMapping("/{id}")
    public PullRequest getPullRequest(@PathVariable Long id) {
        return pullRequestService.getPullRequestById(id);
    }

    @GetMapping("/author/{login}")
    public Set<PullRequest> getPullRequestsByAuthor(@PathVariable String login) {
        return pullRequestService.getPullRequestsByAuthor(login);
    }
}
