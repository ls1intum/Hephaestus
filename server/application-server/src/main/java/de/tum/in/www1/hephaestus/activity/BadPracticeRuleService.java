package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.CreateOrUpdateBadPracticeRuleDTO;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeRule;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeRuleDTO;
import de.tum.in.www1.hephaestus.activity.model.RuleNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.workspace.RepositoryNotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BadPracticeRuleService {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeRuleService.class);

    @Autowired
    private PullRequestBadPracticeRuleRepository pullRequestBadPracticeRuleRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Transactional
    public List<PullRequestBadPracticeRuleDTO> getRules(String repositoryNameWithOwner) {
        logger.info("Getting rules for repository: {}", repositoryNameWithOwner);

        return pullRequestBadPracticeRuleRepository.findByRepositoryName(repositoryNameWithOwner)
                .stream()
                .map(PullRequestBadPracticeRuleDTO::fromPullRequestBadPracticeRule)
                .toList();
    }

    @Transactional
    public PullRequestBadPracticeRuleDTO createRule(String repositoryNameWithOwner, CreateOrUpdateBadPracticeRuleDTO rule) {
        logger.info("Creating rule: {}", rule);

        Repository repository = repositoryRepository.findByNameWithOwner(repositoryNameWithOwner)
                .orElseThrow(() -> new RepositoryNotFoundException(repositoryNameWithOwner));

        PullRequestBadPracticeRule newRule = new PullRequestBadPracticeRule();
        newRule.setTitle(rule.title());
        newRule.setDescription(rule.description());
        newRule.setConditions(rule.conditions());
        newRule.setRepository(repository);
        newRule.setActive(rule.active());

        return  PullRequestBadPracticeRuleDTO.fromPullRequestBadPracticeRule(
                pullRequestBadPracticeRuleRepository.save(newRule));
    }

    @Transactional
    public PullRequestBadPracticeRuleDTO updateRule(long id, CreateOrUpdateBadPracticeRuleDTO rule) {
        logger.info("Updating rule with id: {}", id);

        PullRequestBadPracticeRule existingRule = pullRequestBadPracticeRuleRepository.findById(id).orElse(null);

        if (existingRule == null) {
            throw new RuleNotFoundException(id);
        }

        existingRule.setTitle(rule.title());
        existingRule.setDescription(rule.description());
        existingRule.setConditions(rule.conditions());
        existingRule.setActive(rule.active());

        return PullRequestBadPracticeRuleDTO.fromPullRequestBadPracticeRule(
                pullRequestBadPracticeRuleRepository.save(existingRule));
    }

    @Transactional
    public void deleteRule(long id) {
        logger.info("Deleting rule with id: {}", id);

        PullRequestBadPracticeRule rule = pullRequestBadPracticeRuleRepository.findById(id).orElse(null);

        if (rule == null) {
            throw new RuleNotFoundException(id);
        }

        pullRequestBadPracticeRuleRepository.delete(rule);
    }
}
