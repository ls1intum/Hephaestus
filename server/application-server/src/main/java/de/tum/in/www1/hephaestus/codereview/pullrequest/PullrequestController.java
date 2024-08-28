package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pr")
public class PullRequestController {
    private final PullRequestService pullrequestService;

    public PullRequestController(PullRequestService pullrequestService) {
        this.pullrequestService = pullrequestService;
    }

    @GetMapping("/{id}")
    public PullRequest getPullrequest(@PathVariable Long id) {
        return pullrequestService.getPullrequestById(id);
    }

    @GetMapping("/author/{login}")
    public List<PullRequest> getPullrequestsByAuthor(@PathVariable String login) {
        return pullrequestService.getPullrequestsByAuthor(login);
    }
}
