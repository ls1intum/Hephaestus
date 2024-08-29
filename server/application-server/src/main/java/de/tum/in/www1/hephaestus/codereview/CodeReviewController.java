package de.tum.in.www1.hephaestus.codereview;

import java.io.IOException;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PutMapping("/repository/{nameWithOwner}")
    public Repository addRepository(@PathVariable String nameWithOwner) {
        try {
            return codeReviewService.fetchRepository(nameWithOwner);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
