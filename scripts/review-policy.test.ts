import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { afterEach, beforeEach, describe, it } from "node:test";

import {
	APPROVAL_MARKER,
	approvalBody,
	approvalStands,
	decide,
	enforce,
	type ActionsContext,
	type CreateReviewRequest,
	type GitHubApi,
	type PullRequest,
	type Review,
	parseMaintainers,
} from "./review-policy.ts";

const originalEnvironment = { ...process.env };

const HEAD = "0123456789abcdef0123456789abcdef01234567";
const OTHER = "fedcba9876543210fedcba9876543210fedcba98";

/** A review this module submitted: it carries the marker. */
const ours = (over: Partial<Review> & Pick<Review, "id">): Review => ({
	state: "APPROVED",
	commit_id: HEAD,
	body: approvalBody("Maintainer"),
	user: { login: "github-actions[bot]" },
	...over,
});

/** A review somebody else submitted: no marker. */
const theirs = (over: Partial<Review> & Pick<Review, "id">): Review => ({
	state: "APPROVED",
	commit_id: HEAD,
	body: "Looks good.",
	user: { login: "reviewer" },
	...over,
});

const pull: PullRequest = {
	number: 7,
	head: { sha: HEAD },
	user: { login: "MaIntAiner" },
};

const makeCore = () => {
	const infos: string[] = [];
	const warnings: string[] = [];
	const failures: string[] = [];
	return {
		infos,
		warnings,
		failures,
		info: (message: string) => infos.push(message),
		warning: (message: string) => warnings.push(message),
		setFailed: (message: string) => failures.push(message),
	};
};

interface GitHubOptions {
	readonly resolvedPull?: PullRequest;
	readonly reviews?: readonly Review[];
	readonly failPullWith?: Error;
	readonly failReviewWith?: Error;
}

const makeGitHub = (options: GitHubOptions = {}) => {
	const submitted: CreateReviewRequest[] = [];
	const reviews = [...(options.reviews ?? [])];
	const getPull = () =>
		options.failPullWith
			? Promise.reject(options.failPullWith)
			: Promise.resolve({ data: options.resolvedPull ?? pull });

	const github: GitHubApi = {
		paginate: () => Promise.resolve(reviews),
		rest: {
			pulls: {
				get: getPull,
				listReviews: () => Promise.resolve({ data: reviews }),
				createReview: (params) => {
					if (options.failReviewWith) return Promise.reject(options.failReviewWith);
					submitted.push(params);
					return Promise.resolve({ data: { id: submitted.length } });
				},
			},
		},
	};
	return { submitted, github };
};

/** The single review a run under test is expected to have submitted. */
const onlyReview = (submitted: readonly CreateReviewRequest[]): CreateReviewRequest => {
	assert.equal(submitted.length, 1);
	const [review] = submitted;
	assert.ok(review);
	return review;
};

const context: ActionsContext = {
	eventName: "pull_request_target",
	repo: { owner: "owner", repo: "repo" },
	payload: { pull_request: { number: 7 } },
};

void describe("review policy", () => {
	beforeEach(() => {
		process.env.MAINTAINERS = "Maintainer";
	});

	afterEach(() => {
		process.env = { ...originalEnvironment };
	});

	void describe("parseMaintainers", () => {
		void it("splits, trims, lowercases and drops blanks", () => {
			assert.deepEqual([...parseMaintainers(" One, tWo ,, three,")], ["one", "two", "three"]);
		});

		void it("treats an unset variable as an empty allow-list", () => {
			assert.equal(parseMaintainers(undefined).size, 0);
			assert.equal(parseMaintainers("  ,  ").size, 0);
		});
	});

	void describe("approvalStands", () => {
		void it("recognises this module's own approval of the head commit", () => {
			assert.equal(approvalStands([ours({ id: 1 })], HEAD), true);
		});

		void it("ignores an approval recorded against an earlier commit", () => {
			// `dismiss_stale_reviews_on_push` and the `synchronize` event race; pinning on the commit
			// settles it without waiting for the dismissal to land.
			assert.equal(approvalStands([ours({ id: 1, commit_id: OTHER })], HEAD), false);
		});

		void it("ignores an approval this module did not submit", () => {
			assert.equal(approvalStands([theirs({ id: 1 })], HEAD), false);
		});

		void it("treats its own dismissed approval as no longer standing", () => {
			const reviews = [ours({ id: 1 }), ours({ id: 2, state: "DISMISSED" })];
			assert.equal(approvalStands(reviews, HEAD), false);
		});

		void it("reads the latest decisive review whatever order they arrive in", () => {
			const reviews = [ours({ id: 5 }), ours({ id: 2, state: "DISMISSED" })];
			assert.equal(approvalStands(reviews, HEAD), true);
		});

		void it("lets a comment leave a standing approval alone", () => {
			const reviews = [ours({ id: 1 }), ours({ id: 2, state: "COMMENTED" })];
			assert.equal(approvalStands(reviews, HEAD), true);
		});

		void it("survives a review with no body at all", () => {
			assert.equal(approvalStands([theirs({ id: 1, body: null })], HEAD), false);
		});

		void it("finds no approval in an empty review list", () => {
			assert.equal(approvalStands([], HEAD), false);
		});
	});

	void describe("decide", () => {
		const base = {
			author: "MaIntAiner",
			headSha: HEAD,
			reviews: [] as readonly Review[],
		};

		void it("approves a listed maintainer, matching the login case-insensitively", () => {
			const decision = decide({ ...base, maintainers: new Set(["maintainer"]) });
			assert.equal(decision.kind, "approve");
			assert.match(decision.reason, /REVIEW_POLICY_MAINTAINERS/);
		});

		void it("does nothing for an author who is not listed", () => {
			const decision = decide({
				...base,
				author: "Contributor",
				maintainers: new Set(["maintainer"]),
			});
			assert.equal(decision.kind, "skip");
			assert.match(decision.reason, /needs an approval from someone with write access/);
		});

		void it("approves nobody when the allow-list is empty", () => {
			// The fail-safe direction: with nothing listed, every pull request needs a human.
			assert.equal(decide({ ...base, maintainers: new Set() }).kind, "skip");
		});

		void it("does not approve a second time while its approval stands", () => {
			const decision = decide({
				...base,
				maintainers: new Set(["maintainer"]),
				reviews: [ours({ id: 1 })],
			});
			assert.equal(decision.kind, "standing");
		});

		void it("re-approves once a push has moved the head commit", () => {
			const decision = decide({
				...base,
				maintainers: new Set(["maintainer"]),
				reviews: [ours({ id: 1, commit_id: OTHER })],
			});
			assert.equal(decision.kind, "approve");
		});

		void it("ignores a stranger's approval when deciding whether to approve", () => {
			// A read-access approval does not count toward the ruleset, so it must not talk this
			// module out of supplying one that does.
			const decision = decide({
				...base,
				maintainers: new Set(["maintainer"]),
				reviews: [theirs({ id: 1 })],
			});
			assert.equal(decision.kind, "approve");
		});

		void it("approves a stacked pull request that targets another branch", () => {
			// A layer must already hold its approval when merging the layer below retargets it onto
			// main: that retarget fires no event that could earn one, so declining here would strand
			// the stack at the merge queue.
			assert.equal(decide({ ...base, maintainers: new Set(["maintainer"]) }).kind, "approve");
		});
	});

	void describe("approvalBody", () => {
		void it("carries the marker approvalStands looks for", () => {
			assert.ok(approvalBody("Maintainer").includes(APPROVAL_MARKER));
			assert.equal(approvalStands([ours({ id: 1, body: approvalBody("Maintainer") })], HEAD), true);
		});
	});

	void describe("enforce", () => {
		void it("submits an approving review for a listed maintainer, pinned to the head commit", async () => {
			const { submitted, github } = makeGitHub();
			const core = makeCore();
			await enforce({ github, context, core });

			const review = onlyReview(submitted);
			assert.equal(review.event, "APPROVE");
			assert.equal(review.pull_number, 7);
			assert.equal(review.commit_id, HEAD);
			assert.ok(review.body.includes(APPROVAL_MARKER));
			assert.deepEqual(core.failures, []);
		});

		void it("submits nothing for an author who is not on the allow-list", async () => {
			const { submitted, github } = makeGitHub({
				resolvedPull: { ...pull, user: { login: "Contributor" } },
			});
			const core = makeCore();
			await enforce({ github, context, core });

			assert.deepEqual(submitted, []);
			assert.deepEqual(core.failures, []);
		});

		void it("submits nothing when its own approval already stands", async () => {
			const { submitted, github } = makeGitHub({ reviews: [ours({ id: 1 })] });
			await enforce({ github, context, core: makeCore() });

			assert.deepEqual(submitted, []);
		});

		void it("approves a stacked pull request, so it survives being retargeted onto main", async () => {
			const { submitted, github } = makeGitHub();
			await enforce({ github, context, core: makeCore() });

			assert.equal(submitted.length, 1);
			assert.equal(submitted[0]?.event, "APPROVE");
		});

		void it("warns, and approves nobody, when the allow-list is unset", async () => {
			delete process.env.MAINTAINERS;
			const { submitted, github } = makeGitHub();
			const core = makeCore();
			await enforce({ github, context, core });

			assert.deepEqual(submitted, []);
			assert.equal(core.warnings.length, 1);
			assert.match(String(core.warnings[0]), /REVIEW_POLICY_MAINTAINERS/);
			assert.deepEqual(core.failures, []);
		});

		void it("reads the author from the API rather than from the webhook payload", async () => {
			// The payload is attacker-adjacent under `pull_request_target`; the API is not.
			const { submitted, github } = makeGitHub({
				resolvedPull: { ...pull, user: { login: "Contributor" } },
			});
			await enforce({
				github,
				core: makeCore(),
				context: { ...context, payload: { pull_request: { number: 7 } } },
			});

			assert.deepEqual(submitted, []);
		});

		void it("fails the job when the pull request cannot be read", async () => {
			const { submitted, github } = makeGitHub({ failPullWith: new Error("API is down") });
			const core = makeCore();
			await enforce({ github, context, core });

			assert.deepEqual(submitted, []);
			assert.equal(core.failures.length, 1);
			assert.match(String(core.failures[0]), /API is down/);
		});

		void it("fails the job when the review cannot be submitted", async () => {
			const { github } = makeGitHub({ failReviewWith: new Error("review rejected") });
			const core = makeCore();
			await enforce({ github, context, core });

			assert.equal(core.failures.length, 1);
			assert.match(String(core.failures[0]), /review rejected/);
		});

		void it("fails loudly when an event carries no pull request at all", async () => {
			const { submitted, github } = makeGitHub();
			const core = makeCore();
			await enforce({
				github,
				core,
				context: { eventName: "pull_request_target", repo: context.repo, payload: {} },
			});

			assert.deepEqual(submitted, []);
			assert.equal(core.failures.length, 1);
		});
	});

	// The workflow half of the contract. It runs under `pull_request_target` with
	// `pull-requests: write`, so the hardening below is the security boundary, not a preference.
	void describe("the workflow that applies it", () => {
		const workflow = readFileSync(
			new URL("../.github/workflows/review-policy.yml", import.meta.url),
			"utf8",
		);

		void it("keeps the checkout on the base branch's copy of the policy", () => {
			assert.match(workflow, /ref: \$\{\{ github\.event\.repository\.default_branch \}\}/);
			assert.doesNotMatch(workflow, /ref: \$\{\{ github\.sha \}\}/);
			assert.doesNotMatch(workflow, /github\.event\.pull_request\.head/);
		});

		void it("checks out no more than the policy, and keeps no credentials", () => {
			assert.match(workflow, /persist-credentials: false/);
			assert.match(workflow, /sparse-checkout: scripts/);
		});

		void it("asks for write access to pull requests and nothing else", () => {
			assert.match(workflow, /^permissions: \{\}$/m);
			assert.match(workflow, /pull-requests: write/);
			assert.doesNotMatch(workflow, /contents: write/);
		});

		void it("re-runs on every push, because the ruleset dismisses stale approvals", () => {
			assert.match(workflow, /types: \[opened, reopened, synchronize, ready_for_review, edited\]/);
		});

		void it("runs for a stacked pull request, whatever branch it targets", () => {
			// A `branches:` filter would strand every stack: the retarget onto main that follows the
			// lower layer's merge fires only `edited`, and only on a workflow that runs off main.
			assert.doesNotMatch(workflow, /^\s+branches:/m);
		});

		void it("publishes no check run, now that the ruleset requires a native approval", () => {
			// A required context nothing reports blocks every pull request, including the fix.
			assert.doesNotMatch(workflow, /checks\.create|checks: write/);
			assert.doesNotMatch(workflow, /merge_group/);
		});
	});
});
