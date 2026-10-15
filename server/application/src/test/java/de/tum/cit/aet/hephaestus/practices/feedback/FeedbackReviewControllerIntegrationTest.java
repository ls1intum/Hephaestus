package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.framework.RegisteredItemLabels;
import de.tum.cit.aet.hephaestus.practices.feedback.response.FeedbackResponse;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Membership;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

class FeedbackReviewControllerIntegrationTest extends AbstractFeedbackIntegrationTest {
    @Autowired
    WebTestClient client;

    @MockitoBean
    RegisteredItemLabels labels;

    String route() {
        return "/workspaces/" + workspace.getWorkspaceSlug() + "/practices/reviews/feedback";
    }

    @Test
    @WithAdminUser
    void shouldExposeExactCopyUncertaintyAndRequiredNullableDetailFields() {
        var parent = piece(FeedbackDestination.ON_WORK, FeedbackState.UNCONFIRMED, null);
        summary(parent, FeedbackCopyState.WRITE_STARTED);
        client.get()
                .uri(route() + "/" + parent.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.state")
                .isEqualTo("UNCONFIRMED")
                .jsonPath("$.copyCounts.writeStarted")
                .isEqualTo(1)
                .jsonPath("$.copies[0].writeStartedAt")
                .exists()
                .jsonPath("$.copies[0].externalRef")
                .isEmpty()
                .jsonPath("$.body")
                .isEmpty()
                .jsonPath("$.contentDigest")
                .isEmpty()
                .jsonPath("$.response")
                .isEmpty()
                .jsonPath("$.basis")
                .isArray();
    }

    @Test
    @WithAdminUser
    void shouldUseTheSameStateAndDatePredicatesForPagesAndCounts() {
        var selected = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        piece(FeedbackDestination.ON_WORK, FeedbackState.FAILED, null);
        client.get()
                .uri(route() + "?state=READY&from=2026-10-14T00:00:00Z&to=2026-10-15T00:00:00Z")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(1)
                .jsonPath("$.content[0].id")
                .isEqualTo(selected.getId().toString());
        client.get()
                .uri(route() + "/locations?state=READY&from=2026-10-14T00:00:00Z&to=2026-10-15T00:00:00Z")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].reason")
                .isEqualTo("PRACTICE_PAGES")
                .jsonPath("$[0].count")
                .isEqualTo(1);
    }

    @Test
    @WithAdminUser
    void shouldTreatTheLatestCompleteClearAsNoCurrentDisagreement() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.DELIVERED, AT);
        responses.saveAndFlush(FeedbackResponse.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .feedbackId(parent.getId())
                .resolution(FeedbackResolution.DISAGREE)
                .comment("There is more context.")
                .createdAt(AT)
                .build());
        responses.saveAndFlush(FeedbackResponse.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspace.getId())
                .feedbackId(parent.getId())
                .createdAt(AT.plusSeconds(1))
                .build());
        client.get()
                .uri(route() + "?disagreed=true")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(0);
        client.get()
                .uri(route() + "?disagreed=false")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(1);
    }

    @Test
    @WithAdminUser
    void shouldHideAnotherWorkspacesFeedbackEvenFromAnInstanceAdministrator() {
        var parent = piece(FeedbackDestination.ON_WORK, FeedbackState.UNCONFIRMED, null);
        var other = createWorkspace(
                "feedback-other", "Other workspace", "other-org", AccountType.ORG, persistUser("other-owner"));
        ensureAdminMembership(other);
        client.get()
                .uri("/workspaces/" + other.getWorkspaceSlug() + "/practices/reviews/feedback/" + parent.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
        client.get()
                .uri("/workspaces/" + other.getWorkspaceSlug() + "/practices/reviews/" + reviewId + "/feedback-checks")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
    }

    @Test
    @WithMentorUser
    void shouldRequireWorkspaceAdministrationForFeedbackAndDecisions() {
        ensureMembership(workspace, persistUser("mentor"), Membership.WorkspaceRole.MEMBER);
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        client.get()
                .uri(route())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
        client.get()
                .uri(route() + "/" + parent.getId() + "/decisions")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
        assertThat(decisions.findByWorkspaceIdAndFeedbackIdOrderByDecidedAtDescIdDesc(
                        workspace.getId(), parent.getId()))
                .isEmpty();
    }

    @Test
    @WithAdminUser
    void shouldRejectRetiredOrUnknownStatesAndInvalidPageBounds() {
        client.get()
                .uri(route() + "?state=PREPARED")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(Void.class);
        client.get()
                .uri(route() + "?size=101")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(Void.class);
    }

    @Test
    @WithAdminUser
    void shouldApplyLocationBeforePaginationAndUseTheSameFilteredCounts() {
        piece(FeedbackDestination.ON_WORK, FeedbackState.FAILED, null);
        var first = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var second = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var expected = Stream.of(first, second)
                .min(Comparator.comparing(row -> row.getId().toString()))
                .orElseThrow();
        client.get()
                .uri(route() + "?location=PRACTICE_PAGES&sort=OLDEST&size=1")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(2)
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].id")
                .isEqualTo(expected.getId().toString());
        client.get()
                .uri(route() + "/locations?location=PRACTICE_PAGES")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].reason")
                .isEqualTo("PRACTICE_PAGES")
                .jsonPath("$[0].count")
                .isEqualTo(2);
    }
}
