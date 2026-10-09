package de.tum.cit.aet.hephaestus.core.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.Application;
import de.tum.cit.aet.hephaestus.agent.adapter.EvidenceDeliveryAuthorization;
import de.tum.cit.aet.hephaestus.agent.context.CitedSourceAccess;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobExecutor;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerControlClient;
import de.tum.cit.aet.hephaestus.core.auth.AccountReferenceService;
import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchParticipationQuery;
import de.tum.cit.aet.hephaestus.integration.core.spi.MonitoredRepositoryQuery;
import de.tum.cit.aet.hephaestus.integration.core.webhook.JetStreamPublisher;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmRepositoryItemLifecycle;
import de.tum.cit.aet.hephaestus.integration.scm.github.lifecycle.GitHubLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.scm.github.workspace.GitHubInstallationReconciler;
import de.tum.cit.aet.hephaestus.integration.scm.github.workspace.GitHubWorkspaceProvisioningAdapter;
import de.tum.cit.aet.hephaestus.integration.slack.retention.SlackRetentionPurger;
import de.tum.cit.aet.hephaestus.integration.slack.retention.SlackWorkspaceContentEraser;
import de.tum.cit.aet.hephaestus.integration.slack.retention.SlackWorkspacePurgeAdapter;
import de.tum.cit.aet.hephaestus.practices.result.ResultAdmissionPolicy;
import de.tum.cit.aet.hephaestus.practices.result.ResultMarkService;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.testconfig.NatsTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.nats.client.Nats;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.context.BootstrapUtils;
import org.springframework.test.context.ContextConfiguration;

/** Refreshes the complete application graph without running image pulls or opening worker sessions. */
@Tag("integration")
@ContextConfiguration(classes = Application.class)
class RuntimeRoleContextIntegrationTest {
    /**
     * The webhook role creates its JetStream streams on the shared NATS container at startup. Left there, they
     * make a later test that creates a stream of the same name with its own configuration fail.
     */
    @AfterEach
    void deleteTheStreamsTheRoleCreated() throws Exception {
        var connection = Nats.connect(NatsTestContainer.getServerUrl());
        try {
            var streams = connection.jetStreamManagement();
            for (String name : streams.getStreamNames()) {
                streams.deleteStream(name);
            }
        } finally {
            connection.close();
        }
    }

    @ParameterizedTest
    @EnumSource(RuntimeRole.class)
    void shouldResolveEveryApplicationBeanWhenOnlyOneRuntimeRoleIsEnabled(RuntimeRole role) {
        var database = PostgreSQLTestContainer.createMigratedDatabase(
                "runtime_role_" + role.name().toLowerCase(Locale.ROOT));
        var properties = Arrays.stream(RuntimeRole.values())
                .map(candidate -> candidate.property() + "=" + (candidate == role))
                .toArray(String[]::new);
        var configuration =
                BootstrapUtils.resolveTestContextBootstrapper(getClass()).buildMergedContextConfiguration();
        new WebApplicationContextRunner()
                .withInitializer(application -> {
                    var conversion = new ApplicationConversionService();
                    application.getEnvironment().setConversionService(conversion);
                    application.getBeanFactory().setConversionService(conversion);
                })
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(application -> configuration
                        .getContextCustomizers()
                        .forEach(customizer -> customizer.customizeContext(application, configuration)))
                .withUserConfiguration(Application.class)
                .withPropertyValues(
                        "spring.profiles.active="
                                + (role == RuntimeRole.SERVER
                                        ? "default"
                                        : role.name().toLowerCase(Locale.ROOT)),
                        "spring.config.import=classpath:application-test.yml",
                        "spring.main.allow-bean-definition-overriding=false",
                        "spring.main.lazy-initialization=false",
                        "spring.sql.init.mode=never",
                        "spring.datasource.url=" + database.jdbcUrl(),
                        "spring.datasource.username=" + database.username(),
                        "spring.datasource.password=" + database.password(),
                        "hephaestus.agent.enabled=true",
                        "hephaestus.worker.control.endpoint=wss://127.0.0.1:1/control",
                        "hephaestus.worker.control.registration-token=role-context-test",
                        "hephaestus.worker.capacity.review-max=1",
                        "hephaestus.worker.capacity.mentor-max=1",
                        "hephaestus.sync.nats.enabled=" + (role != RuntimeRole.WORKER),
                        "hephaestus.sync.nats.server=" + NatsTestContainer.getServerUrl(),
                        "hephaestus.sync.nats.username=",
                        "hephaestus.sync.nats.password=",
                        "hephaestus.integration.slack.enabled=true",
                        "hephaestus.integration.outline.enabled=true")
                .withPropertyValues(properties)
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(RuntimeRole.enabled(application.getEnvironment()))
                            .containsExactly(role);
                    assertThat(application.getBeansOfType(ScmRepositoryItemLifecycle.class))
                            .hasSize(role == RuntimeRole.SERVER ? 1 : 0);
                    if (role == RuntimeRole.SERVER) {
                        assertThat(application.getBeansWithAnnotation(WorkspaceScopedController.class))
                                .isNotEmpty();
                        assertThat(application).hasSingleBean(AccountReferenceService.class);
                        assertThat(application).hasSingleBean(ResearchParticipationQuery.class);
                        assertThat(application).hasSingleBean(MonitoredRepositoryQuery.class);
                        assertThat(application).hasSingleBean(PracticeReviewCoverageService.class);
                        assertThat(application).hasSingleBean(ResultAdmissionPolicy.class);
                        assertThat(application).hasSingleBean(GitHubLifecycleListener.class);
                        assertThat(application).hasSingleBean(GitHubInstallationReconciler.class);
                        assertThat(application).hasSingleBean(GitHubWorkspaceProvisioningAdapter.class);
                        assertThat(application).hasSingleBean(SlackRetentionPurger.class);
                        assertThat(application).hasSingleBean(SlackWorkspaceContentEraser.class);
                        assertThat(application).hasSingleBean(SlackWorkspacePurgeAdapter.class);
                    } else {
                        assertThat(application.getBeansWithAnnotation(WorkspaceScopedController.class))
                                .isEmpty();
                        assertThat(application).doesNotHaveBean(AccountReferenceService.class);
                        assertThat(application).doesNotHaveBean(ResearchParticipationQuery.class);
                        assertThat(application).doesNotHaveBean(MonitoredRepositoryQuery.class);
                        assertThat(application).doesNotHaveBean(PracticeReviewCoverageService.class);
                        assertThat(application).doesNotHaveBean(GitHubLifecycleListener.class);
                        assertThat(application).doesNotHaveBean(GitHubInstallationReconciler.class);
                        assertThat(application).doesNotHaveBean(GitHubWorkspaceProvisioningAdapter.class);
                        assertThat(application).doesNotHaveBean(SlackRetentionPurger.class);
                        assertThat(application).doesNotHaveBean(SlackWorkspaceContentEraser.class);
                        assertThat(application).doesNotHaveBean(SlackWorkspacePurgeAdapter.class);
                    }
                    if (role == RuntimeRole.WORKER) {
                        assertThat(application).hasSingleBean(ReviewJobExecutor.class);
                        assertThat(application).hasSingleBean(WorkerControlClient.class);
                        assertThat(application).hasSingleBean(ResultAdmissionPolicy.class);
                    } else {
                        assertThat(application).doesNotHaveBean(ReviewJobExecutor.class);
                    }
                    if (role == RuntimeRole.WEBHOOK) {
                        assertThat(application).hasSingleBean(JetStreamPublisher.class);
                        assertThat(application).doesNotHaveBean(ResultAdmissionPolicy.class);
                        assertThat(application).doesNotHaveBean(ResultMarkService.class);
                        assertThat(application).doesNotHaveBean(CitedSourceAccess.class);
                        assertThat(application).doesNotHaveBean(EvidenceDeliveryAuthorization.class);
                    } else {
                        assertThat(application).doesNotHaveBean(JetStreamPublisher.class);
                        assertThat(application).hasSingleBean(ResultMarkService.class);
                        assertThat(application).hasSingleBean(CitedSourceAccess.class);
                        assertThat(application).hasSingleBean(EvidenceDeliveryAuthorization.class);
                    }
                });
    }
}
