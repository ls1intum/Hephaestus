package de.tum.cit.aet.hephaestus.integration.chat.item;

import de.tum.cit.aet.hephaestus.integration.outline.documentation.OutlineDocumentPermission;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.docs.item.DocumentContentSource;
import de.tum.cit.aet.hephaestus.integration.docs.item.DocumentItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.outline.documentation.OutlineDocumentProjector;
import de.tum.cit.aet.hephaestus.integration.outline.documentation.OutlineDocumentSelector;
import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineDocumentRepository;
import de.tum.cit.aet.hephaestus.integration.outline.item.DocumentItemSource;
import de.tum.cit.aet.hephaestus.integration.slack.capture.ConversationCaptureSource;
import de.tum.cit.aet.hephaestus.integration.slack.capture.ThreadCaptureSource;
import de.tum.cit.aet.hephaestus.integration.slack.conversation.SlackConversationProjector;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackPersonErasureService;
import de.tum.cit.aet.hephaestus.integration.slack.item.ThreadItemSource;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ChatDocsRoleStartupTest extends BaseUnitTest {
    @Test
    void shouldKeepDescriptorsAndOmitCaptureBeansWhenOnlyTheWebhookRoleStarts() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        ThreadItemDescriptor.class,
                        DocumentItemDescriptor.class,
                        ThreadItemSource.class,
                        DocumentItemSource.class,
                        ThreadCaptureSource.class,
                        SlackConversationProjector.class,
                        OutlineDocumentProjector.class,
                        OutlineDocumentSelector.class, OutlineDocumentPermission.class,
                        ConversationCaptureSource.class,
                        SlackPersonErasureService.class)
                .withPropertyValues(
                        "hephaestus.runtime.server.enabled=false",
                        "hephaestus.runtime.worker.enabled=false",
                        "hephaestus.runtime.webhook.enabled=true",
                        "hephaestus.integration.slack.enabled=true",
                        "hephaestus.integration.outline.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(ThreadItemDescriptor.class)
                            .hasSingleBean(DocumentItemDescriptor.class);
                    assertThat(context)
                            .doesNotHaveBean(ThreadItemSource.class)
                            .doesNotHaveBean(DocumentItemSource.class)
                            .doesNotHaveBean(ThreadCaptureSource.class)
                            .doesNotHaveBean(SlackConversationProjector.class)
                            .doesNotHaveBean(OutlineDocumentProjector.class)
                            .doesNotHaveBean(OutlineDocumentSelector.class).doesNotHaveBean(OutlineDocumentPermission.class)
                            .doesNotHaveBean(ConversationCaptureSource.class)
                            .doesNotHaveBean(SlackPersonErasureService.class);
                });
    }

    @Test
    void shouldPublishCaptureWithoutServerErasureOrMentorBeansWhenOnlyTheWorkerRoleStarts() {
        verifyCaptureRole(false);
    }

    @Test
    void shouldPublishCaptureAndErasureWhenTheServerRoleStarts() {
        verifyCaptureRole(true);
    }

    private static void verifyCaptureRole(boolean server) {
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(
                        ThreadItemDescriptor.class,
                        DocumentItemDescriptor.class,
                        ThreadItemSource.class,
                        DocumentItemSource.class,
                        ThreadCaptureSource.class,
                        ConversationCaptureSource.class,
                        SlackPersonErasureService.class)
                .withPropertyValues(
                        "hephaestus.runtime.server.enabled=" + server,
                        "hephaestus.runtime.worker.enabled=" + !server,
                        "hephaestus.runtime.webhook.enabled=false",
                        "hephaestus.integration.slack.enabled=true",
                        "hephaestus.integration.outline.enabled=true")
                .withBean(SlackThreadRepository.class, () -> mock(SlackThreadRepository.class))
                .withBean(SlackMessageRepository.class, () -> mock(SlackMessageRepository.class))
                .withBean(SlackConversationProjector.class, () -> mock(SlackConversationProjector.class))
                .withBean(OutlineDocumentRepository.class, () -> mock(OutlineDocumentRepository.class))
                .withBean(OutlineDocumentProjector.class, () -> mock(OutlineDocumentProjector.class))
                .withBean(OutlineDocumentPermission.class, () -> mock(OutlineDocumentPermission.class))
                .withBean(ConnectionService.class, () -> mock(ConnectionService.class))
                .withBean(GitProviderRegistry.class, () -> mock(GitProviderRegistry.class))
                .withBean(ObjectMapper.class, JsonMapper::new)
                .withBean(
                        DocumentContentSource.class,
                        () -> new DocumentContentSource(JsonMapper.builder().build()));
        if (server)
            runner = runner.withBean(ConversationFeedbackErasure.class, () -> mock(ConversationFeedbackErasure.class));
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context)
                    .hasSingleBean(ThreadItemSource.class)
                    .hasSingleBean(DocumentItemSource.class)
                    .hasSingleBean(ThreadCaptureSource.class);
            if (server)
                assertThat(context)
                        .hasSingleBean(SlackPersonErasureService.class)
                        .hasSingleBean(ConversationCaptureSource.class);
            else
                assertThat(context)
                        .doesNotHaveBean(SlackPersonErasureService.class)
                        .doesNotHaveBean(ConversationCaptureSource.class);
        });
    }
}
