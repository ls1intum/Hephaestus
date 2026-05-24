/**
 * SCM family library.
 *
 * <p>Holds family-shared abstractions used by all source-code-management vendors
 * (GitHub, GitLab, plus future Bitbucket / Forgejo / Gitea):
 * <ul>
 *   <li>{@code model/} — domain entities (PullRequest, Issue, Review, Label, …)
 *   <li>{@code events/} — {@code GitDomainEvent} sealed hierarchy
 *   <li>{@code feedback/} — {@code ScmFeedbackChannel}, {@code ScmInlineFindingChannel}, {@code ScmApprovalChannel}
 *   <li>{@code spi/} — {@code GitContentPlatform}, {@code ScmOperations}, {@code ScmSubject}, {@code CommitStatusReporter}
 * </ul>
 *
 * <p>Vendor packages ({@code integration/github/...}, {@code integration/gitlab/...}) implement
 * these SPIs. Vendor-only extensions (GitHub App, Projects V2, Discussions) live in the
 * vendor package, NOT here.
 */
@org.springframework.modulith.NamedInterface({"api", "events"})
package de.tum.cit.aet.hephaestus.integration.scm;
