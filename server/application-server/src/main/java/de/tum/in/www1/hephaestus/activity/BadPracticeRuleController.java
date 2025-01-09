package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.CreateOrUpdateBadPracticeRuleDTO;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeRuleDTO;
import de.tum.in.www1.hephaestus.activity.model.RuleNotFoundException;
import de.tum.in.www1.hephaestus.workspace.RepositoryNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rules")
public class BadPracticeRuleController {

    @Autowired
    private BadPracticeRuleService badPracticeRuleService;

    @GetMapping("/{owner}/{repository}")
    public ResponseEntity<List<PullRequestBadPracticeRuleDTO>> getRulesByRepository(@PathVariable String owner, @PathVariable String repository) {
        List<PullRequestBadPracticeRuleDTO> rules = badPracticeRuleService.getRules(owner + "/" + repository);
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/{owner}/{repository}")
    public ResponseEntity<PullRequestBadPracticeRuleDTO> createRule(@PathVariable String owner, @PathVariable String repository, @RequestBody CreateOrUpdateBadPracticeRuleDTO rule) {
        try {
            PullRequestBadPracticeRuleDTO createdRule = badPracticeRuleService.createRule(owner + "/" + repository, rule);
            return ResponseEntity.ok(createdRule);
        } catch (RepositoryNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<PullRequestBadPracticeRuleDTO> updateRule(@PathVariable long id, @RequestBody CreateOrUpdateBadPracticeRuleDTO rule) {
        try {
            PullRequestBadPracticeRuleDTO updatedRule = badPracticeRuleService.updateRule(id, rule);
            return ResponseEntity.ok(updatedRule);
        } catch (RuleNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable long id) {
        try {
            badPracticeRuleService.deleteRule(id);
            return ResponseEntity.ok().build();
        } catch (RuleNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
