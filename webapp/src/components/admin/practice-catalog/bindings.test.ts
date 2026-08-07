import { describe, expect, it } from "vitest";
import type { PracticeAutomatedReviewPolicy, PracticeBinding } from "@/api/types.gen";
import {
	artifactKindOfBindings,
	artifactKindOfSignal,
	bindingsProblem,
	claimedSignals,
	normalizeBinding,
	orderedWorkTypes,
	recommendedBinding,
	roleOf,
	withRole,
} from "@/components/admin/practice-catalog/bindings";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";

const pullRequests = mockPracticeDefinitionOptions.workTypes[0];
const aiSupported = pullRequests.recommendedPolicy;
const guidanceOnly: PracticeAutomatedReviewPolicy = {
	...aiSupported,
	automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" },
	knownLimitations: [],
};

function binding(overrides: Partial<PracticeBinding> = {}): PracticeBinding {
	return {
		signals: ["scm.pull_request.opened"],
		needs: [{ sourceKind: "scm.pull-request.core", stance: "REQUIRED" }],
		...overrides,
	};
}

describe("artifactKindOfSignal", () => {
	it("reads the kind off everything before the last dot, as the server does", () => {
		expect(artifactKindOfSignal("scm.pull_request.merged")).toBe("scm.pull_request");
		expect(artifactKindOfSignal("chat.conversation_thread.settled")).toBe(
			"chat.conversation_thread",
		);
	});

	it("does not invent a kind for a set of bindings that names no signal", () => {
		expect(artifactKindOfBindings([])).toBeUndefined();
		expect(artifactKindOfBindings([binding({ signals: [] })])).toBeUndefined();
	});
});

describe("normalizeBinding", () => {
	it("puts a binding in the shape the server stores it so an untouched form is not dirty", () => {
		const normalized = normalizeBinding({
			signals: ["scm.pull_request.ready", "scm.pull_request.opened", "scm.pull_request.ready"],
			needs: [
				{ sourceKind: "scm.pull-request.diff", stance: "REQUIRED" },
				{ sourceKind: "scm.pull-request.core", stance: "REQUIRED" },
			],
		});

		expect(normalized.signals).toEqual(["scm.pull_request.opened", "scm.pull_request.ready"]);
		expect(normalized.needs.map((need) => need.sourceKind)).toEqual([
			"scm.pull-request.core",
			"scm.pull-request.diff",
		]);
	});

	it("omits onDrafts entirely when it is false, matching a binding that never mentions drafts", () => {
		expect(normalizeBinding(binding({ onDrafts: false }))).not.toHaveProperty("onDrafts");
		expect(normalizeBinding(binding({ onDrafts: true })).onDrafts).toBe(true);
	});
});

describe("withRole", () => {
	it("moves a source between stances without leaving the old one behind", () => {
		const contextual = withRole(binding().needs, "scm.pull-request.core", "CONTEXTUAL");

		expect(contextual).toEqual([{ sourceKind: "scm.pull-request.core", stance: "CONTEXTUAL" }]);
	});

	it("drops the source when it stops being used", () => {
		expect(withRole(binding().needs, "scm.pull-request.core", "NOT_USED")).toEqual([]);
		expect(roleOf(binding().needs, "scm.repository.tree")).toBe("NOT_USED");
	});

	it("keeps the needs sorted the way the server stores them", () => {
		const added = withRole(binding().needs, "scm.pull-request.comments", "CONTEXTUAL");

		expect(added.map((need) => need.sourceKind)).toEqual([
			"scm.pull-request.comments",
			"scm.pull-request.core",
		]);
	});
});

describe("recommendedBinding", () => {
	it("starts a new occasion on the recommended moments with the recommended evidence", () => {
		const fresh = recommendedBinding(pullRequests);

		expect(fresh.signals).toEqual([
			"scm.pull_request.opened",
			"scm.pull_request.ready",
			"scm.pull_request.synchronized",
		]);
	});

	it("never claims a moment an existing occasion already owns", () => {
		const first = recommendedBinding(pullRequests);
		const second = recommendedBinding(pullRequests, [...claimedSignals([first])]);

		expect(second.signals).not.toHaveLength(0);
		expect(second.signals.some((signal) => first.signals.includes(signal))).toBe(false);
	});

	it("falls back to the first free moment when none of them is recommended", () => {
		const recommended = pullRequests.signals
			.filter((option) => option.recommended)
			.map((option) => option.signal);

		expect(recommendedBinding(pullRequests, recommended).signals).toEqual([
			"scm.pull_request.reviewed",
		]);
	});
});

describe("orderedWorkTypes", () => {
	it("leads with the kinds this build knows, in the order it offers them", () => {
		const shuffled = {
			workTypes: [...mockPracticeDefinitionOptions.workTypes].sort((left, right) =>
				left.artifactKind.localeCompare(right.artifactKind),
			),
		};

		expect(orderedWorkTypes(shuffled).map((option) => option.artifactKind)).toEqual([
			"scm.pull_request",
			"scm.issue",
			"chat.conversation_thread",
		]);
	});

	it("keeps a kind it has never heard of rather than dropping it", () => {
		const withUnknown = {
			workTypes: [
				{ ...pullRequests, artifactKind: "docs.page" },
				...mockPracticeDefinitionOptions.workTypes,
			],
		};

		expect(orderedWorkTypes(withUnknown).map((option) => option.artifactKind)).toEqual([
			"scm.pull_request",
			"scm.issue",
			"chat.conversation_thread",
			"docs.page",
		]);
	});
});

describe("bindingsProblem", () => {
	it("accepts the shape a new practice starts in", () => {
		expect(
			bindingsProblem([recommendedBinding(pullRequests)], aiSupported, pullRequests),
		).toBeUndefined();
	});

	it("refuses a practice with nothing to start it", () => {
		expect(bindingsProblem([], aiSupported, pullRequests)?.message).toBe(
			"Add at least one occasion that starts a review.",
		);
	});

	it("refuses an occasion with no moment, which the server answers with a 500", () => {
		const problem = bindingsProblem([binding({ signals: [] })], aiSupported, pullRequests);

		expect(problem?.message).toBe("Choose when this occasion starts a review.");
		expect(problem?.focusId).toBe("practice-binding-0-signals");
	});

	it("refuses the same moment claimed twice, which the server rejects outright", () => {
		const problem = bindingsProblem([binding(), binding()], aiSupported, pullRequests);

		expect(problem?.message).toBe(
			"Two occasions start on the same moment. Merge them or change one.",
		);
		expect(problem?.focusId).toBe("practice-binding-1-signals");
	});

	it("refuses a moment that belongs to another kind of work", () => {
		expect(
			bindingsProblem([binding({ signals: ["scm.issue.opened"] })], aiSupported, pullRequests)
				?.message,
		).toBe("One of the chosen moments does not apply to this kind of work.");
	});

	it("holds every occasion to naming evidence the review cannot run without", () => {
		const problem = bindingsProblem(
			[
				binding(),
				binding({
					signals: ["scm.pull_request.merged"],
					needs: [{ sourceKind: "scm.pull-request.core", stance: "CONTEXTUAL" }],
				}),
			],
			aiSupported,
			pullRequests,
		);

		expect(problem?.message).toBe(
			"Every occasion needs at least one source the review cannot run without.",
		);
		expect(problem?.focusId).toBe("practice-binding-1-evidence");
	});

	it("counts an exhaustive stance as evidence the review cannot run without", () => {
		expect(
			bindingsProblem(
				[
					binding({
						needs: [{ sourceKind: "scm.review-threads", stance: "EXHAUSTIVE" }],
					}),
				],
				aiSupported,
				pullRequests,
			),
		).toBeUndefined();
	});

	it("refuses an absence claim resting on a source that can never be captured whole", () => {
		expect(
			bindingsProblem(
				[binding({ needs: [{ sourceKind: "scm.linked-work-items", stance: "EXHAUSTIVE" }] })],
				aiSupported,
				pullRequests,
			)?.message,
		).toBe(
			"One source can never be captured whole, so nothing this review says about what is missing from it can rest on it.",
		);
	});

	it("refuses evidence on a practice that runs no automated review", () => {
		expect(bindingsProblem([binding()], guidanceOnly, pullRequests)?.message).toBe(
			"Guidance only cannot read any evidence.",
		);
		expect(bindingsProblem([binding({ needs: [] })], guidanceOnly, pullRequests)).toBeUndefined();
	});
});
