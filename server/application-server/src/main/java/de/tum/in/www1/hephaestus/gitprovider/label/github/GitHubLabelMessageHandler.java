package de.tum.in.www1.hephaestus.gitprovider.label.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub label webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 */
@Component
public class GitHubLabelMessageHandler extends GitHubMessageHandler<GitHubLabelEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubLabelMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final LabelRepository labelRepository;

    GitHubLabelMessageHandler(ProcessingContextFactory contextFactory, LabelRepository labelRepository) {
        super(GitHubLabelEventDTO.class);
        this.contextFactory = contextFactory;
        this.labelRepository = labelRepository;
    }

    @Override
    protected String getEventKey() {
        return "label";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubLabelEventDTO event) {
        GitHubLabelDTO labelDto = event.label();

        if (labelDto == null) {
            logger.warn("Received label event with missing data");
            return;
        }

        logger.info(
            "Received label event: action={}, label={}, repo={}",
            event.action(),
            labelDto.name(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        if ("deleted".equals(event.action())) {
            labelRepository.deleteById(labelDto.id());
        } else {
            processLabel(labelDto, context);
        }
    }

    private Label processLabel(GitHubLabelDTO dto, ProcessingContext context) {
        return labelRepository
            .findById(dto.id())
            .map(label -> {
                if (dto.name() != null) label.setName(dto.name());
                if (dto.color() != null) label.setColor(dto.color());
                return labelRepository.save(label);
            })
            .orElseGet(() -> {
                Label label = new Label();
                label.setId(dto.id());
                label.setName(dto.name());
                label.setColor(dto.color());
                label.setRepository(context.repository());
                return labelRepository.save(label);
            });
    }
}
