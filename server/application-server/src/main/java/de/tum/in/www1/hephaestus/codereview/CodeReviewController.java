package de.tum.in.www1.hephaestus.codereview;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.hephaestus.codereview.repository.Repository;

@RestController
@RequestMapping("/codereview")
public class CodeReviewController {
    private final CodeReviewService codeReviewService;

    public CodeReviewController(CodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }

    /**
     * Retrieves all {@link Repository} entities.
     * 
     * @return A list of all Repository entities
     */
    @GetMapping
    public List<Repository> getAllRepositories() {
        return codeReviewService.getAllRepositories();
    }
}
