package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import java.util.Optional;
import java.util.Set;

import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<PullRequest> getPullRequest(@PathVariable Long id) {
        Optional<PullRequest> pullRequest =pullRequestService.getPullRequestById(id);
        return pullRequest.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/author/{login}")
    public Set<PullRequest> getPullRequestsByAuthor(@PathVariable String login) {
        return pullRequestService.getPullRequestsByAuthor(login);
    }
}
