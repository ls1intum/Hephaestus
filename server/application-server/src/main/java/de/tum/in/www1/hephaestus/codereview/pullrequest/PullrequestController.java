package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pullrequest")
public class PullRequestController {
    private final PullRequestService pullrequestService;

    public PullRequestController(PullRequestService pullrequestService) {
        this.pullrequestService = pullrequestService;
    }

    @GetMapping("/{id}")
    public PullRequest getPullRequest(@PathVariable Long id) {
        return pullrequestService.getPullRequestById(id);
    }

    @GetMapping("/author/{login}")
    public Set<PullRequest> getPullRequestsByAuthor(@PathVariable String login) {
        return pullrequestService.getPullRequestsByAuthor(login);
    }
}
