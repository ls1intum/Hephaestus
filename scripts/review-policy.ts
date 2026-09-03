/**
 * The review policy, expressed as a review rather than as a check run.
 *
 * The `main` ruleset now requires one native approval. This module supplies it for the authors the
 * repository has decided need no second reader — the logins listed in `REVIEW_POLICY_MAINTAINERS` —
 * by submitting an approving review with `GITHUB_TOKEN`, which GitHub attributes to
 * `github-actions[bot]` and counts toward `required_approving_review_count`. Every other author gets
 * nothing from this module, so their pull request sits in GitHub's own "Review required" state until
 * a human with write access approves it.
 *
 * Two properties this replaces the old custom check run for. A pull request awaiting review now
 * reads as *awaiting review* in the merge box instead of carrying a permanently yellow required
 * context, and the checks list on a healthy pull request goes fully green. The enforcement is
 * GitHub's, not ours: the count is evaluated at merge and at merge-queue entry, and an armed
 * auto-merge will not fire while it is unmet.
 *
 * The policy this encodes — "a maintainer may merge their own work" — exists only because the
 * repository has a single write-access collaborator, so there is nobody else to ask. The moment a
 * second reviewer holds write access, delete this workflow and the allow-list with it and let the
 * native requirement stand on its own.
 *
 * The decision reads the author login and nothing else. It never reads the diff, the title, the body
 * or any other pull-request-controlled content, which is what makes it safe to run under
 * `pull_request_target` with `pull-requests: write`.
 */

type ApiMethod<T> = (params: Record<string, unknown>) => Promise<{ data: T }>;

export interface Review {
	readonly id: number;
	readonly node_id: string;
	readonly state: string;
	readonly commit_id: string;
	readonly body?: string | null;
	readonly user: { readonly login: string } | null;
}

export interface PullRequest {
	readonly number: number;
	readonly head: { readonly sha: string };
	readonly user: { readonly login: string };
}

/** The subset of the review-creation request this module ever sends. */
export interface CreateReviewRequest {
	readonly owner: string;
	readonly repo: string;
	readonly pull_number: number;
	readonly commit_id: string;
	readonly event: string;
	readonly body: string;
}

export interface GitHubApi {
	readonly graphql: (query: string, variables: { subjectId: string }) => Promise<unknown>;
	readonly paginate: (
		endpoint: ApiMethod<Review[]>,
		params: Record<string, unknown>,
	) => Promise<Review[]>;
	readonly rest: {
		readonly pulls: {
			readonly get: ApiMethod<PullRequest>;
			readonly listReviews: ApiMethod<Review[]>;
			readonly createReview: (
				params: CreateReviewRequest,
			) => Promise<{ data: { readonly id: number } }>;
		};
	};
}

export interface ActionsContext {
	readonly eventName: string;
	readonly repo: { readonly owner: string; readonly repo: string };
	readonly payload: {
		readonly pull_request?: { readonly number: number };
	};
}

export interface ActionsCore {
	readonly info: (message: string) => void;
	readonly warning: (message: string) => void;
	readonly setFailed: (message: string) => void;
}

export interface EnforceInput {
	readonly github: GitHubApi;
	readonly context: ActionsContext;
	readonly core: ActionsCore;
}

/**
 * The branch whose ruleset requires the approval this module supplies. Nothing here filters on it:
 * an approval on a pull request targeting any other branch satisfies no rule and is inert, whereas
 * filtering strands every stacked pull request — the lower layer merges, GitHub retargets the upper
 * one onto `main`, and a base-filtered policy has already declined to approve it.
 */
export const POLICY_BASE_REF = "main";

/**
 * Marks the reviews this module submitted, so a re-run recognises its own standing approval and adds
 * no second one. Matching on the marker rather than on the reviewer login keeps a human approval —
 * which may or may not carry write access, and so may or may not count — out of the decision: the
 * only approval this module reasons about is the one it is responsible for.
 */
export const APPROVAL_MARKER = "<!-- review-policy: maintainer auto-approval -->";

/** Review states that replace a reviewer's previous position. `COMMENTED` leaves it standing. */
const DECISIVE_STATES = new Set(["APPROVED", "CHANGES_REQUESTED", "DISMISSED"]);

const shortSha = (sha: string): string => sha.slice(0, 7);

export const parseMaintainers = (raw: string | undefined): Set<string> =>
	new Set(
		(raw ?? "")
			.split(",")
			.map((login) => login.trim().toLowerCase())
			.filter(Boolean),
	);

/**
 * Whether this module's own approval still covers `headSha`.
 *
 * The ruleset sets `dismiss_stale_reviews_on_push`, so a push both invalidates the previous approval
 * and fires `synchronize`. Pinning on `commit_id` rather than on the dismissal state settles that
 * race in the safe direction: an approval recorded against an earlier commit does not count here
 * either way, so a re-approval is submitted whether or not GitHub has finished dismissing it yet.
 */
const ourReviews = (reviews: readonly Review[]): Review[] =>
	reviews
		.filter((entry) => (entry.body ?? "").includes(APPROVAL_MARKER))
		.toSorted((left, right) => left.id - right.id);

export const approvalStands = (reviews: readonly Review[], headSha: string): boolean => {
	const ours = ourReviews(reviews).filter((entry) => DECISIVE_STATES.has(entry.state));

	const latest = ours.at(-1);
	return latest !== undefined && latest.state === "APPROVED" && latest.commit_id === headSha;
};

const minimizeReview = `mutation MinimizeReview($subjectId: ID!) {
	minimizeComment(input: { subjectId: $subjectId, classifier: OUTDATED }) {
		minimizedComment {
			isMinimized
		}
	}
}`;

const minimizeSupersededApprovals = async (
	github: GitHubApi,
	reviews: readonly Review[],
	core: ActionsCore,
): Promise<void> => {
	for (const review of reviews) {
		try {
			await github.graphql(minimizeReview, { subjectId: review.node_id });
		} catch (error) {
			const reason = error instanceof Error ? error.message : String(error);
			core.warning(`Could not minimize superseded automatic approval ${review.id}: ${reason}`);
		}
	}
};

export type DecisionKind = "approve" | "standing" | "skip";

export interface Decision {
	readonly kind: DecisionKind;
	readonly reason: string;
}

export interface DecisionInput {
	readonly author: string;
	readonly headSha: string;
	readonly maintainers: ReadonlySet<string>;
	readonly reviews: readonly Review[];
}

/**
 * What this module should do about one pull request. Purely a function of the author login, the head
 * commit and the approvals already on record — never of anything the author wrote.
 */
export const decide = (input: DecisionInput): Decision => {
	if (!input.maintainers.has(input.author.toLowerCase())) {
		return {
			kind: "skip",
			reason:
				`@${input.author} is not listed in REVIEW_POLICY_MAINTAINERS, so this pull request ` +
				"needs an approval from someone with write access.",
		};
	}

	if (approvalStands(input.reviews, input.headSha)) {
		return {
			kind: "standing",
			reason: `This pull request is already approved for ${shortSha(input.headSha)}.`,
		};
	}

	return {
		kind: "approve",
		reason:
			`@${input.author} is listed in REVIEW_POLICY_MAINTAINERS, so ${shortSha(input.headSha)} ` +
			"is approved on the policy's behalf.",
	};
};

/** The body of the review this module submits, marker included. */
export const approvalBody = (author: string): string =>
	`${APPROVAL_MARKER}\nApproved automatically: @${author} is listed in the ` +
	"`REVIEW_POLICY_MAINTAINERS` repository variable, which the repository treats as satisfying the " +
	"review requirement. See the review policy in `docs/contributor/ci-cd.mdx`.";

/**
 * Applies the review policy to one pull request event.
 *
 * The pull request is re-read from the API rather than trusted from the webhook payload, so the
 * author login and head commit come from GitHub rather than from the event that woke the workflow.
 * A failure here fails the job: nothing is approved, and the native requirement leaves the pull
 * request blocked, which is the safe direction.
 */
export const enforce = async ({ github, context, core }: EnforceInput): Promise<void> => {
	const eventPullRequest = context.payload.pull_request;
	if (!eventPullRequest) {
		core.setFailed(`A ${context.eventName} event carried no pull request to evaluate.`);
		return;
	}

	try {
		const { data: pull } = await github.rest.pulls.get({
			...context.repo,
			pull_number: eventPullRequest.number,
		});

		const maintainers = parseMaintainers(process.env.MAINTAINERS);
		if (maintainers.size === 0) {
			// Fail-safe, and quietly so: with nobody allow-listed every pull request simply needs a
			// human approval, which is what the native requirement asks for anyway. It is worth saying
			// out loud because a maintainer whose own pull request stopped self-approving would
			// otherwise have to guess why.
			core.warning(
				"REVIEW_POLICY_MAINTAINERS is empty or unset, so no pull request is auto-approved. " +
					"Every pull request now needs an approval from someone with write access.",
			);
		}

		const reviews = await github.paginate(github.rest.pulls.listReviews, {
			...context.repo,
			pull_number: pull.number,
			per_page: 100,
		});

		const decision = decide({
			author: pull.user.login,
			headSha: pull.head.sha,
			maintainers,
			reviews,
		});
		core.info(decision.reason);
		if (decision.kind === "skip") return;
		if (decision.kind === "standing") {
			await minimizeSupersededApprovals(github, ourReviews(reviews).slice(0, -1), core);
			return;
		}

		// `commit_id` pins the approval to the commit the decision was made against. If a push landed
		// between the read and this call, the approval attaches to the commit that was evaluated and
		// the ruleset treats it as stale for the new head — and the push's own `synchronize` event
		// produces a fresh one.
		await github.rest.pulls.createReview({
			...context.repo,
			pull_number: pull.number,
			commit_id: pull.head.sha,
			event: "APPROVE",
			body: approvalBody(pull.user.login),
		});
		await minimizeSupersededApprovals(github, ourReviews(reviews), core);
		core.info(`Approved ${shortSha(pull.head.sha)} on pull request #${pull.number}.`);
	} catch (error) {
		const reason = error instanceof Error ? error.message : String(error);
		core.setFailed(`The review policy could not be applied: ${reason}`);
	}
};
