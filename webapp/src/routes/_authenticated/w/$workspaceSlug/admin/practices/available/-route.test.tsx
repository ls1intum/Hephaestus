import { fireEvent, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CatalogPracticePreview } from "@/api/types.gen";
import { mockPractices } from "@/components/admin/practices/story-mock-data";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { server } from "@/mocks/server";
import { ROUTE_RENDER_WAIT, renderRouteAt, renderRouteAtWithRouter } from "@/test/router-harness";

const preview: CatalogPracticePreview = {
	slug: "describe-what-and-why",
	availability: "AVAILABLE",
	etag: '"reviewed-plan"',
	initialAutonomy: "HUMAN_APPROVAL",
	sourceReviewRuleFingerprint: mockAuthorDeclaredEvidenceValidation.reviewRuleFingerprint,
	area: {
		slug: "review-ready-work",
		disposition: "CREATE_CATALOG_AREA",
		definition: { name: "Review-ready work" },
	},
	definition: {
		name: "Describe what changed and why",
		artifactKind: "scm.pull_request",
		bindings: [mockPullRequestBinding],
		automatedReviewPolicy: mockPullRequestPolicy,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
		criteria: "Explain the change and why it is needed.",
		whyItMatters: "Reviewers need intent.",
		whatGoodLooksLike: "A concise summary and motivation.",
		areaSlug: "review-ready-work",
	},
};

describe("available practice routes", () => {
	beforeEach(() => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practices/definition-options", () =>
				HttpResponse.json(mockPracticeDefinitionOptions),
			),
			http.get("*/workspaces/:workspaceSlug/members/me", () =>
				HttpResponse.json({ role: "ADMIN", userId: 1, userLogin: "ada", userName: "Ada" }),
			),
			http.get("*/workspaces", () =>
				HttpResponse.json([
					{
						id: 1,
						workspaceSlug: "acme",
						displayName: "Acme",
						providerType: "GITHUB",
						status: "ACTIVE",
						practicesEnabled: false,
						mentorEnabled: false,
						achievementsEnabled: false,
						leaderboardEnabled: false,
						progressionEnabled: false,
						leaguesEnabled: false,
					},
				]),
			),
		);
	});

	it("shows all offered practices and distinguishes adoption states", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption", () =>
				HttpResponse.json([
					{
						slug: preview.slug,
						name: preview.definition.name,
						artifactKind: "scm.pull_request",
						areaSlug: "review-ready-work",
						availability: "AVAILABLE",
						automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
					},
					{
						slug: "already-there",
						name: "Already there",
						artifactKind: "scm.issue",
						availability: "ADOPTED",
						automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
					},
					{
						slug: "local-collision",
						name: "Local collision",
						artifactKind: "scm.issue",
						availability: "SLUG_CONFLICT",
						automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
					},
				]),
			),
		);

		renderRouteAt("/w/acme/admin/practices/available");

		await screen.findByRole("heading", { name: "Available practices" }, ROUTE_RENDER_WAIT);
		expect(await screen.findByText("Available")).not.toBeNull();
		expect(await screen.findByText("Adopted")).not.toBeNull();
		expect(await screen.findByText("Slug conflict")).not.toBeNull();
		expect(await screen.findAllByText("Not independently validated")).toHaveLength(3);
		expect(
			screen.getByRole("link", { name: `Review practice: ${preview.definition.name}` }),
		).not.toBeNull();
		expect(screen.getByRole("link", { name: "Review practice: Already there" })).not.toBeNull();
		expect(screen.getByRole("link", { name: "Review practice: Local collision" })).not.toBeNull();
	});

	it("adopts the reviewed practice and opens the workspace copy", async () => {
		const adoptedPractice = mockPractices[0];
		if (!adoptedPractice) throw new Error("Expected a practice fixture");
		const seenIfMatch = vi.fn();
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () =>
				HttpResponse.json(preview),
			),
			http.post("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", ({ request }) => {
				seenIfMatch(request.headers.get("If-Match"));
				return HttpResponse.json(
					{
						...adoptedPractice,
						slug: preview.slug,
						name: preview.definition.name,
						autonomy: {
							effective: "HUMAN_APPROVAL",
							override: "HUMAN_APPROVAL",
							source: "PRACTICE",
							inherited: false,
						},
					},
					{ status: 201 },
				);
			}),
			http.get("*/workspaces/:workspaceSlug/practices/:practiceSlug", () =>
				HttpResponse.json({
					...adoptedPractice,
					slug: preview.slug,
					name: preview.definition.name,
					autonomy: {
						effective: "HUMAN_APPROVAL",
						override: "HUMAN_APPROVAL",
						source: "PRACTICE",
						inherited: false,
					},
				}),
			),
		);

		const { router } = renderRouteAtWithRouter(`/w/acme/admin/practices/available/${preview.slug}`);
		fireEvent.click(
			await screen.findByRole("button", { name: "Adopt practice" }, ROUTE_RENDER_WAIT),
		);

		await waitFor(() => expect(seenIfMatch).toHaveBeenCalledWith(preview.etag));
		await waitFor(
			() => expect(router.state.location.pathname).toBe(`/w/acme/admin/practices/${preview.slug}`),
			ROUTE_RENDER_WAIT,
		);
	});

	it("pins adoption to the reviewed ETag and requires review again after a 412", async () => {
		const seenIfMatch = vi.fn();
		let previewReads = 0;
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () => {
				previewReads += 1;
				return HttpResponse.json(
					{
						...preview,
						definition: {
							...preview.definition,
							criteria:
								previewReads > 1
									? "Updated review rule that must be reviewed."
									: preview.definition.criteria,
						},
						etag: previewReads > 1 ? '"updated-plan"' : preview.etag,
					},
					{ headers: { ETag: previewReads > 1 ? '"updated-plan"' : preview.etag } },
				);
			}),
			http.post("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", ({ request }) => {
				seenIfMatch(request.headers.get("If-Match"));
				return HttpResponse.json(
					{ title: "Precondition Failed", status: 412, detail: "The adoption preview changed." },
					{ status: 412 },
				);
			}),
		);

		renderRouteAt(`/w/acme/admin/practices/available/${preview.slug}`);
		const adopt = await screen.findByRole("button", { name: "Adopt practice" }, ROUTE_RENDER_WAIT);
		fireEvent.click(adopt);

		await waitFor(() => expect(seenIfMatch).toHaveBeenCalledWith(preview.etag));
		const changed = await screen.findByRole("heading", {
			name: "The adoption preview changed",
		});
		expect(document.activeElement).toBe(changed);
		expect(await screen.findByText("Updated review rule that must be reviewed.")).not.toBeNull();
	});

	it("does not claim the latest preview is shown when refreshing after a 412 fails", async () => {
		let previewReads = 0;
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () => {
				previewReads += 1;
				return previewReads === 1
					? HttpResponse.json(preview)
					: HttpResponse.json(
							{ title: "Service Unavailable", status: 503, detail: "Try again later." },
							{ status: 503 },
						);
			}),
			http.post("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () =>
				HttpResponse.json(
					{ title: "Precondition Failed", status: 412, detail: "The adoption preview changed." },
					{ status: 412 },
				),
			),
		);

		renderRouteAt(`/w/acme/admin/practices/available/${preview.slug}`);
		fireEvent.click(
			await screen.findByRole("button", { name: "Adopt practice" }, ROUTE_RENDER_WAIT),
		);

		await screen.findByText("Couldn't load the adoption preview", {}, ROUTE_RENDER_WAIT);
		expect(screen.queryByRole("heading", { name: "The adoption preview changed" })).toBeNull();
		expect(
			screen.queryByText("The latest definition or workspace outcome is now shown."),
		).toBeNull();
	});

	it("recovers a concurrent adoption by showing the workspace copy", async () => {
		server.use(
			http.get("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () =>
				HttpResponse.json(preview),
			),
			http.post("*/workspaces/:workspaceSlug/practice-catalog/adoption/:slug", () => {
				return HttpResponse.json(
					{
						type: "about:blank",
						title: "Conflict",
						status: 409,
						detail: "A workspace practice already uses this slug.",
					},
					{ status: 409 },
				);
			}),
			http.get("*/workspaces/:workspaceSlug/practices/:practiceSlug", () =>
				HttpResponse.json({
					...mockPractices[0],
					slug: preview.slug,
					name: preview.definition.name,
				}),
			),
		);

		const { router } = renderRouteAtWithRouter(`/w/acme/admin/practices/available/${preview.slug}`);
		fireEvent.click(
			await screen.findByRole("button", { name: "Adopt practice" }, ROUTE_RENDER_WAIT),
		);

		await waitFor(
			() => expect(router.state.location.pathname).toBe(`/w/acme/admin/practices/${preview.slug}`),
			ROUTE_RENDER_WAIT,
		);
	});
});
