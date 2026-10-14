package de.tum.cit.aet.hephaestus.integration.scm.gitlab.subissue;

import static de.tum.cit.aet.hephaestus.integration.scm.GraphQlResponseStubValidator.Vendor.GITLAB;
import static de.tum.cit.aet.hephaestus.integration.scm.GraphQlResponseStubValidator.assertVendorCouldReturn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.graphql.FragmentMergingDocumentSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResult;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlResponseHandler;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClientInterceptor;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Exercises the real hierarchy walk and committed parent links through native GraphQL JSON decoding. */
@Tag("integration")
class GitLabSubIssueReconciliationIntegrationTest extends BaseIntegrationTest {

    private static final String PROJECT = "team/hierarchy";

    @Autowired
    private IdentityProviderRepository providers;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private IssueRepository issues;

    @Autowired
    private GitLabGraphQlResponseHandler responseHandler;

    @Autowired
    private TransactionTemplate transactions;

    @PersistenceContext
    private @Nullable EntityManager entityManager;

    private final Deque<String> answers = new ArrayDeque<>();
    private final List<String> requestedCursors = new ArrayList<>();

    private Repository repository;
    private Issue parent;
    private Issue existingChild;
    private Issue newChild;
    private Issue unrelatedParent;
    private Issue unrelatedChild;
    private GitLabSubIssueSyncService service;

    @BeforeEach
    void setUp() {
        var provider = providers.save(new IdentityProvider(
                IdentityProviderType.GITLAB, "https://" + UUID.randomUUID() + ".gitlab.example.test"));
        repository = repository(provider, 1, PROJECT);
        parent = issue(repository, 1, 1, null);
        existingChild = issue(repository, 2, 2, parent);
        newChild = issue(repository, 3, 3, null);
        var unrelatedRepository = repository(provider, 2, "team/unrelated");
        unrelatedParent = issue(unrelatedRepository, 4, 1, null);
        unrelatedChild = issue(unrelatedRepository, 5, 2, unrelatedParent);
        var currentEntityManager = Objects.requireNonNull(entityManager);
        transactions.executeWithoutResult(status -> {
            currentEntityManager.flush();
            currentEntityManager.clear();
        });
        assertThat(parentId(existingChild)).contains(parent.getId());
        assertThat(parentId(newChild)).isEmpty();
        assertUnrelatedLink();

        var clients = mock(GitLabGraphQlClientProvider.class);
        when(clients.forScope(1L)).thenReturn(gitLabClient());
        service = new GitLabSubIssueSyncService(issues, repositories, clients, responseHandler);
    }

    @Test
    void shouldPreserveStoredParentAndAbortWhenProjectIsNull() {
        answers.add("""
                {"data":{"project":null}}
                """);

        var result = sync();
        var storedParent = parentId(existingChild);

        assertSoftly(softly -> {
            softly.assertThat(storedParent)
                    .as("committed parent after an unavailable project")
                    .contains(parent.getId());
            softly.assertThat(result).isEqualTo(SyncResult.abortedError(0));
            softly.assertThat(parentId(unrelatedChild)).contains(unrelatedParent.getId());
        });
    }

    @Test
    void shouldRemoveStoredParentAndCompleteWhenFinalListingIsEmpty() {
        answers.add(page(0, false, null));

        var result = sync();

        assertThat(parentId(existingChild)).isEmpty();
        assertThat(result).isEqualTo(SyncResult.completed(0));
        assertUnrelatedLink();
    }

    @Test
    void shouldRemoveStoredParentWhenCompleteInventoryReportsChildWithoutParent() {
        answers.add(page(4, false, null, node(1, null), """
                {"iid":"2","widgets":[{"type":"DESCRIPTION"},
                 {"type":"HIERARCHY","hasParent":false,"parent":null,"hasChildren":false}]}
                """, node(3, null), """
                {"iid":"99","widgets":null}
                """));

        var result = sync();

        assertThat(parentId(existingChild)).isEmpty();
        assertThat(parentId(newChild)).isEmpty();
        assertThat(result).isEqualTo(SyncResult.completed(0));
        assertUnrelatedLink();
    }

    @ParameterizedTest(name = "{index}: widgets={0}")
    @ValueSource(strings = {"null", "[]", "[{\"type\":\"DESCRIPTION\"}]"})
    void shouldPreserveUnknownParentAndReconcileLaterSiblingsWhenMirroredWorkItemHasNoReadableHierarchy(
            String widgets) {
        var removedChild = issue(repository, 6, 4, parent);
        assertThat(parentId(removedChild)).contains(parent.getId());
        answers.add(page(4, false, null, """
                {"iid":"2","widgets":%s}
                """.formatted(widgets), node(3, 1), node(4, null), node(1, null, true)));

        var result = sync();

        assertSoftly(softly -> {
            softly.assertThat(parentId(existingChild)).contains(parent.getId());
            softly.assertThat(parentId(newChild)).contains(parent.getId());
            softly.assertThat(parentId(removedChild)).isEmpty();
            softly.assertThat(result).isEqualTo(SyncResult.abortedError(1));
            softly.assertThat(parentId(unrelatedChild)).contains(unrelatedParent.getId());
        });
    }

    @Test
    void shouldReconcileLaterSiblingsAndRecoverWhenUnavailableDeclaredParentBecomesReadable() {
        var removedChild = issue(repository, 6, 4, parent);
        assertThat(parentId(removedChild)).contains(parent.getId());
        answers.add(page(4, false, null, """
                {"iid":"2","widgets":[{"type":"HIERARCHY","hasParent":true,
                 "parent":null,"hasChildren":false}]}
                """, node(3, 1), node(4, null), node(1, null, true)));

        var result = sync();

        assertSoftly(softly -> {
            softly.assertThat(parentId(existingChild)).contains(parent.getId());
            softly.assertThat(parentId(newChild)).contains(parent.getId());
            softly.assertThat(parentId(removedChild)).isEmpty();
            softly.assertThat(result).isEqualTo(SyncResult.abortedError(1));
            softly.assertThat(parentId(unrelatedChild)).contains(unrelatedParent.getId());
        });

        answers.add(page(4, false, null, node(2, null), node(3, 1), node(4, null), node(1, null, true)));

        var recovered = sync();

        assertThat(parentId(existingChild)).isEmpty();
        assertThat(parentId(newChild)).contains(parent.getId());
        assertThat(parentId(removedChild)).isEmpty();
        assertThat(recovered).isEqualTo(SyncResult.completed(0));
        assertUnrelatedLink();
    }

    @Test
    void shouldReconcileLaterPageWhenFirstPageContainsUnknownHierarchy() {
        var removedChild = issue(repository, 6, 4, parent);
        assertThat(parentId(removedChild)).contains(parent.getId());
        answers.add(page(4, true, "after-unknown", """
                {"iid":"2","widgets":null}
                """));
        answers.add(page(4, false, null, node(3, 1), node(4, null), node(1, null, true)));

        var result = sync();

        assertSoftly(softly -> {
            softly.assertThat(parentId(existingChild)).contains(parent.getId());
            softly.assertThat(parentId(newChild)).contains(parent.getId());
            softly.assertThat(parentId(removedChild)).isEmpty();
            softly.assertThat(result).isEqualTo(SyncResult.abortedError(1));
            softly.assertThat(requestedCursors).containsExactly("<start>", "after-unknown");
            softly.assertThat(parentId(unrelatedChild)).contains(unrelatedParent.getId());
        });
    }

    @Test
    void shouldPreserveStoredParentsWhenPageFailsAfterUnknownHierarchyAndReadableSiblings() {
        var removedChild = issue(repository, 6, 4, parent);
        assertThat(parentId(removedChild)).contains(parent.getId());
        answers.add(page(4, true, "after-unknown", """
                {"iid":"2","widgets":null}
                """, node(3, 1), node(4, null), node(1, null, true)));
        answers.add("""
                {"data":null,"errors":[{"message":"Internal server error"}]}
                """);

        var result = sync();

        assertSoftly(softly -> {
            softly.assertThat(parentId(existingChild)).contains(parent.getId());
            softly.assertThat(parentId(newChild)).contains(parent.getId());
            softly.assertThat(parentId(removedChild)).contains(parent.getId());
            softly.assertThat(result).isEqualTo(SyncResult.abortedError(1));
            softly.assertThat(requestedCursors).containsExactly("<start>", "after-unknown");
            softly.assertThat(parentId(unrelatedChild)).contains(unrelatedParent.getId());
        });
    }

    @Test
    void shouldRetainLaterPageParentAndPersistNewLinkWhenAllPagesComplete() {
        answers.add(page(3, true, "next", node(3, 1)));
        answers.add(page(3, false, null, node(1, null, true), node(2, 1)));

        var result = sync();

        assertThat(parentId(existingChild)).contains(parent.getId());
        assertThat(parentId(newChild)).contains(parent.getId());
        assertThat(result).isEqualTo(SyncResult.completed(1));
        assertThat(requestedCursors).containsExactly("<start>", "next");
        assertUnrelatedLink();
    }

    @Test
    void shouldPreserveUnobservedParentAndProcessedCountWhenNextPageFailsThenRecover() {
        answers.add(page(3, true, "next", node(3, 1)));
        answers.add("""
                {"data":null,"errors":[{"message":"Internal server error"}]}
                """);

        var incomplete = sync();
        var unobservedParent = parentId(existingChild);
        var processedParent = parentId(newChild);
        var unrelatedAfterFailure = parentId(unrelatedChild);

        answers.add(page(3, false, null, node(1, null, true), node(2, null), node(3, 1)));
        var recovered = sync();

        assertSoftly(softly -> {
            softly.assertThat(unobservedParent).contains(parent.getId());
            softly.assertThat(processedParent).contains(parent.getId());
            softly.assertThat(unrelatedAfterFailure).contains(unrelatedParent.getId());
            softly.assertThat(incomplete).isEqualTo(SyncResult.abortedError(1));
            softly.assertThat(parentId(existingChild)).isEmpty();
            softly.assertThat(parentId(newChild)).contains(parent.getId());
            softly.assertThat(recovered).isEqualTo(SyncResult.completed(0));
            softly.assertThat(parentId(unrelatedChild)).contains(unrelatedParent.getId());
        });
        assertThat(requestedCursors).containsExactly("<start>", "next", "<start>");
    }

    @Test
    void shouldFollowEmptyNonterminalPageWhenValidContinuationCompletesInventory() {
        answers.add(page(3, true, "next"));
        answers.add(page(3, false, null, node(1, null, true), node(2, 1), node(3, 1)));

        var result = sync();

        assertThat(parentId(existingChild)).contains(parent.getId());
        assertThat(parentId(newChild)).contains(parent.getId());
        assertThat(result).isEqualTo(SyncResult.completed(1));
        assertThat(requestedCursors).containsExactly("<start>", "next");
        assertUnrelatedLink();
    }

    @Test
    void shouldPreserveStoredParentAndAbortWhenValidDataHasHierarchyFieldError() {
        // WorkItemWidgetHierarchy.parent is nullable, so its error leaves the rest of the data valid.
        answers.add("""
                {"data":{"project":{"workItems":{"count":3,
                 "pageInfo":{"hasNextPage":false,"endCursor":null},
                 "nodes":[
                  {"iid":"1","widgets":[{"type":"HIERARCHY","hasParent":false,
                   "parent":null,"hasChildren":true}]},
                  {"iid":"2","widgets":[{"type":"HIERARCHY","hasParent":true,
                   "parent":null,"hasChildren":false}]},
                  {"iid":"3","widgets":[{"type":"HIERARCHY","hasParent":false,
                   "parent":null,"hasChildren":false}]}]}}},
                 "errors":[{"message":"Internal server error",
                  "path":["project","workItems","nodes",1,"widgets",0,"parent"]}]}
                """);

        var result = sync();

        assertSoftly(softly -> {
            softly.assertThat(parentId(existingChild)).contains(parent.getId());
            softly.assertThat(parentId(newChild)).isEmpty();
            softly.assertThat(result).isEqualTo(SyncResult.abortedError(0));
            softly.assertThat(parentId(unrelatedChild)).contains(unrelatedParent.getId());
        });
    }

    private SyncResult sync() {
        var currentEntityManager = Objects.requireNonNull(entityManager);
        var result = transactions.execute(status -> {
            var storedRepository = repositories.findById(repository.getId()).orElseThrow();
            var synced = service.syncSubIssuesForRepository(1L, storedRepository);
            currentEntityManager.flush();
            currentEntityManager.clear();
            return synced;
        });
        assertThat(answers).as("every supplied page must be consumed").isEmpty();
        return Objects.requireNonNull(result);
    }

    private Optional<Long> parentId(Issue issue) {
        var currentEntityManager = Objects.requireNonNull(entityManager);
        return Objects.requireNonNull(transactions.execute(status -> {
            currentEntityManager.clear();
            var stored = issues.findById(issue.getId()).orElseThrow();
            return Optional.ofNullable(stored.getParentIssue()).map(Issue::getId);
        }));
    }

    private void assertUnrelatedLink() {
        assertThat(parentId(unrelatedChild)).contains(unrelatedParent.getId());
    }

    private Repository repository(IdentityProvider provider, long nativeId, String path) {
        var created = new Repository();
        created.setProvider(provider);
        created.setNativeId(nativeId);
        created.setName(path.substring(path.lastIndexOf('/') + 1));
        created.setNameWithOwner(path);
        created.setHtmlUrl(provider.getServerUrl() + "/" + path);
        created.setPushedAt(Instant.now());
        created.setVisibility(Repository.Visibility.PRIVATE);
        return repositories.save(created);
    }

    private Issue issue(Repository repo, long nativeId, int iid, @Nullable Issue parentIssue) {
        var created = new Issue();
        created.setProvider(repo.getProvider());
        created.setNativeId(nativeId);
        created.setRepository(repo);
        created.setNumber(iid);
        created.setTitle("Issue " + iid);
        created.setState(Issue.State.OPEN);
        created.setParentIssue(parentIssue);
        return issues.save(created);
    }

    private static String node(int iid, @Nullable Integer parentIid) {
        return node(iid, parentIid, false);
    }

    private static String node(int iid, @Nullable Integer parentIid, boolean hasChildren) {
        String parentJson =
                parentIid == null ? "null" : """
                {"iid":"%d","namespace":{"fullPath":"%s"}}
                """.formatted(parentIid, PROJECT).strip();
        return """
                {"iid":"%d","widgets":[{"type":"HIERARCHY","hasParent":%s,
                 "parent":%s,"hasChildren":%s}]}
                """.formatted(iid, parentIid != null, parentJson, hasChildren);
    }

    private static String page(int count, boolean hasNextPage, @Nullable String cursor, String... nodes) {
        return """
                {"data":{"project":{"workItems":{"count":%d,"nodes":[%s],
                 "pageInfo":{"hasNextPage":%s,"endCursor":%s}}}}}
                """.formatted(
                        count, String.join(",", nodes), hasNextPage, cursor == null ? "null" : "\"" + cursor + "\"");
    }

    private HttpGraphQlClient gitLabClient() {
        var webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(answers.removeFirst())
                        .build()));
        GraphQlClientInterceptor recordRequest = new GraphQlClientInterceptor() {
            @Override
            public Mono<ClientGraphQlResponse> intercept(ClientGraphQlRequest request, Chain chain) {
                requestedCursors.add(Objects.toString(request.getVariables().get("after"), "<start>"));
                return chain.next(request).doOnNext(response -> {
                    if (response.isValid()) {
                        assertVendorCouldReturn(
                                GITLAB,
                                "GetProjectWorkItemHierarchy",
                                "project",
                                response.field("project").getValue());
                    }
                });
            }
        };
        return HttpGraphQlClient.builder(webClient)
                .url("https://gitlab.example.test/api/graphql")
                .documentSource(new FragmentMergingDocumentSource(
                        List.of(
                                new ClassPathResource("graphql/gitlab/operations/"),
                                new ClassPathResource("graphql/gitlab/fragments/")),
                        List.of(".graphql", ".gql"),
                        List.of(new ClassPathResource("graphql/gitlab/fragments/GitLabUserFields.graphql"))))
                .interceptor(recordRequest)
                .build();
    }
}
