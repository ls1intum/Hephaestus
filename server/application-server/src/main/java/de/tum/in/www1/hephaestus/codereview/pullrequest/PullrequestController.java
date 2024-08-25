package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pr")
public class PullrequestController {
    private final PullrequestService pullrequestService;

    public PullrequestController(PullrequestService pullrequestService) {
        this.pullrequestService = pullrequestService;
    }

    @GetMapping("/{id}")
    public Pullrequest getPullrequest(@PathVariable Long id) {
        return pullrequestService.getPullrequestById(id);
    }

    @GetMapping("/author/{login}")
    public List<Pullrequest> getPullrequestsByAuthor(@PathVariable String login) {
        return pullrequestService.getPullrequestsByAuthor(login);
    }
}
