/**
 * The review policy the `main` ruleset requires, published as a check run of its own.
 *
 * The policy itself is unchanged: an author listed in `REVIEW_POLICY_MAINTAINERS` satisfies it, and
 * every other author needs an approval of the current head commit from a non-author who holds write
 * access. What changed is how the verdict is reported. The workflow job used to *be* the required
 * `review-policy` context and failed while an approval was outstanding, so a healthy pull request
 * waiting its turn looked broken on the pull request list. The job now always succeeds and this
 * module publishes the verdict as a separate check run under the required name, which lets
 * "waiting" be a pending check rather than a red one.
 */

type ApiMethod<T> = (params: Record<string, unknown>) => Promise<{ data: T }>;

export interface Review {
	readonly id: number;
	readonly state: string;
	readonly commit_id: string;
	readonly user: { readonly login: string } | null;
}

export interface PullRequest {
	readonly number: number;
	readonly base: { readonly ref: string };
	readonly head: { readonly sha: string };
	readonly user: { readonly login: string };
}

/** The subset of the Checks API request this module ever sends. */
export interface CheckRunRequest {
	readonly owner: string;
	readonly repo: string;
	readonly name: string;
	readonly head_sha: string;
	readonly status: string;
	readonly conclusion?: string;
	readonly details_url?: string;
	readonly output: { readonly title: string; readonly summary: string };
}

export interface GitHubApi {
	readonly paginate: (
		endpoint: ApiMethod<Review[]>,
		params: Record<string, unknown>,
	) => Promise<Review[]>;
	readonly rest: {
		readonly checks: {
			readonly create: (params: CheckRunRequest) => Promise<{ data: { readonly id: number } }>;
		};
		readonly pulls: {
			readonly get: ApiMethod<PullRequest>;
			readonly listReviews: ApiMethod<Review[]>;
		};
		readonly repos: {
			readonly getCollaboratorPermissionLevel: ApiMethod<{ readonly permission: string }>;
		};
	};
}

export interface ActionsContext {
	readonly eventName: string;
	readonly repo: { readonly owner: string; readonly repo: string };
	readonly payload: {
		readonly pull_request?: {
			readonly number: number;
			readonly head?: { readonly sha: string };
		};
	};
}

export interface ActionsCore {
	readonly info: (message: string) => void;
	readonly setFailed: (message: string) => void;
}

export interface EnforceInput {
	readonly github: GitHubApi;
	readonly context: ActionsContext;
	readonly core: ActionsCore;
}

export type VerdictKind = "satisfied" | "waiting" | "misconfigured";

export interface Verdict {
	readonly kind: VerdictKind;
	readonly title: string;
	readonly summary: string;
}

/**
 * The context name the `main` ruleset requires, bound there to the GitHub Actions integration —
 * which a `GITHUB_TOKEN`-authenticated check run is attributed to. Changing this string without
 * editing the ruleset in the same breath leaves every pull request waiting on a context nothing
 * reports, so no pull request can merge and no fix can land.
 */
export const CHECK_NAME = "review-policy";

/** The policy guards the branch the ruleset guards; `pull_request_review` carries no base filter. */
export const POLICY_BASE_REF = "main";

const WRITE_PERMISSIONS = new Set(["admin", "maintain", "write"]);

/** Review states that replace a reviewer's previous position. `COMMENTED` leaves it standing. */
const DECISIVE_STATES = new Set(["APPROVED", "CHANGES_REQUESTED", "DISMISSED"]);

/**
 * GitHub treats a required check that concludes `neutral` or `skipped` as passing — "Required
 * status checks must have a `successful`, `skipped`, or `neutral` status before collaborators can
 * make changes to a protected branch" (GitHub Docs, *About protected branches*) — so neither may
 * stand for "waiting". A check run that has not completed is pending instead, and a pending
 * required context blocks the merge button and merge-queue entry just as a failing one does: a
 * pull request "must have passed all required branch protection checks" before it can be queued,
 * and a workflow that leaves its required check pending "will be blocked from merging" (GitHub
 * Docs, *Troubleshooting required status checks*). Pending buys the same enforcement and reads as
 * waiting rather than broken.
 */
const CHECK_STATE: Record<VerdictKind, { readonly status: string; readonly conclusion?: string }> =
	{
		satisfied: { status: "completed", conclusion: "success" },
		waiting: { status: "in_progress" },
		misconfigured: { status: "completed", conclusion: "failure" },
	};

const shortSha = (sha: string): string => sha.slice(0, 7);

export const parseMaintainers = (raw: string | undefined): Set<string> =>
	new Set(
		(raw ?? "")
			.split(",")
			.map((login) => login.trim().toLowerCase())
			.filter(Boolean),
	);

/**
 * Reviewers whose latest decisive review approves `headSha`, the author excluded. Reviews arrive
 * oldest-first by id, so the last one a reviewer left is the one that counts; an approval of an
 * earlier commit does not carry to a commit nobody has read.
 */
export const currentApprovers = (
	reviews: readonly Review[],
	author: string,
	headSha: string,
): string[] => {
	const latestByReviewer = new Map<string, Review>();
	for (const review of reviews.toSorted((left, right) => left.id - right.id)) {
		if (
			review.user &&
			review.user.login.toLowerCase() !== author.toLowerCase() &&
			DECISIVE_STATES.has(review.state)
		) {
			latestByReviewer.set(review.user.login.toLowerCase(), review);
		}
	}

	const approvers: string[] = [];
	for (const [login, review] of latestByReviewer) {
		if (review.state === "APPROVED" && review.commit_id === headSha) approvers.push(login);
	}
	return approvers;
};

export interface DecisionInput {
	readonly author: string;
	readonly headSha: string;
	readonly maintainers: ReadonlySet<string>;
	readonly reviews: readonly Review[];
	readonly permissionOf: (login: string) => Promise<string>;
}

/** The policy verdict, and the sentence a reader needs to know what happens next. */
export const decide = async (input: DecisionInput): Promise<Verdict> => {
	if (input.maintainers.has(input.author.toLowerCase())) {
		return {
			kind: "satisfied",
			title: `@${input.author} is a listed maintainer`,
			summary:
				`@${input.author} is listed in the \`REVIEW_POLICY_MAINTAINERS\` repository variable, ` +
				"so this pull request needs no separate approval to satisfy the review policy.",
		};
	}

	for (const login of currentApprovers(input.reviews, input.author, input.headSha)) {
		const permission = await input.permissionOf(login);
		if (WRITE_PERMISSIONS.has(permission)) {
			return {
				kind: "satisfied",
				title: `Approved by @${login}`,
				summary:
					`@${login} holds \`${permission}\` access and approved commit ` +
					`\`${shortSha(input.headSha)}\`. Pushing a new commit dismisses that approval and ` +
					"returns this check to waiting.",
			};
		}
	}

	// An empty allow-list is a repository misconfiguration rather than a pull request that is
	// waiting its turn, and only says so once no approval has satisfied the policy anyway — so the
	// set of pull requests this lets through is unchanged.
	if (input.maintainers.size === 0) {
		return {
			kind: "misconfigured",
			title: "REVIEW_POLICY_MAINTAINERS is not set",
			summary:
				"The `REVIEW_POLICY_MAINTAINERS` repository variable is empty or unset, so the author " +
				"allow-list cannot be evaluated and no approval on this pull request satisfies the " +
				"review policy. A repository administrator has to set it.",
		};
	}

	return {
		kind: "waiting",
		title: "Waiting for a maintainer approval",
		summary:
			"This pull request is waiting, not broken.\n\n" +
			"**To unblock it:** someone other than the author, holding write access, has to approve " +
			`commit \`${shortSha(input.headSha)}\`. GitHub does not accept an approval from the ` +
			`author, so @${input.author} cannot clear this alone.\n\n` +
			"Pushing a new commit dismisses existing approvals and returns this check here.",
	};
};

/** Where the check run's "Details" link points, so a reader can reach the run that decided it. */
const detailsUrl = (context: ActionsContext): string | undefined => {
	const run = process.env.GITHUB_RUN_ID;
	if (!run) return undefined;
	const server = process.env.GITHUB_SERVER_URL ?? "https://github.com";
	return `${server}/${context.repo.owner}/${context.repo.repo}/actions/runs/${run}`;
};

const publish = async (
	github: GitHubApi,
	context: ActionsContext,
	core: ActionsCore,
	headSha: string,
	verdict: Verdict,
): Promise<void> => {
	// A fresh check run rather than an update: GitHub silently ignores a status that walks a
	// completed check run back to `in_progress`, which is exactly the dismissed-approval case. It
	// keeps only the newest run per app and name on a commit, so creating one leaves no stale
	// duplicate behind either.
	await github.rest.checks.create({
		...context.repo,
		name: CHECK_NAME,
		head_sha: headSha,
		...CHECK_STATE[verdict.kind],
		details_url: detailsUrl(context),
		output: { title: verdict.title, summary: verdict.summary },
	});
	core.info(`${CHECK_NAME} on ${shortSha(headSha)} is ${verdict.kind}: ${verdict.title}`);
};

const evaluate = async (
	github: GitHubApi,
	context: ActionsContext,
	pull: PullRequest,
): Promise<Verdict> => {
	if (pull.base.ref !== POLICY_BASE_REF) {
		return {
			kind: "satisfied",
			title: `Not required for base branch ${pull.base.ref}`,
			summary: `The review policy guards pull requests that target \`${POLICY_BASE_REF}\`.`,
		};
	}

	const reviews = await github.paginate(github.rest.pulls.listReviews, {
		...context.repo,
		pull_number: pull.number,
		per_page: 100,
	});

	return await decide({
		author: pull.user.login,
		headSha: pull.head.sha,
		maintainers: parseMaintainers(process.env.MAINTAINERS),
		reviews,
		permissionOf: async (login) => {
			const { data } = await github.rest.repos.getCollaboratorPermissionLevel({
				...context.repo,
				username: login,
			});
			return data.permission;
		},
	});
};

/**
 * Publishes the required `review-policy` check run for one pull request event. The merge queue is
 * deliberately not routed here: the workflow answers `merge_group` inline, so the queue's path
 * needs neither a checkout nor this module, and a commit added to the queue before this file
 * reached the default branch still gets its context.
 */
export const enforce = async ({ github, context, core }: EnforceInput): Promise<void> => {
	const pullRequest = context.payload.pull_request;
	if (!pullRequest) {
		core.setFailed(`A ${context.eventName} event carried no pull request to evaluate.`);
		return;
	}

	// The webhook's own head SHA, kept only so a failure that happens before the pull request is
	// read still has a commit to report against.
	let headSha = pullRequest.head?.sha;
	try {
		const { data: pull } = await github.rest.pulls.get({
			...context.repo,
			pull_number: pullRequest.number,
		});
		headSha = pull.head.sha;
		await publish(github, context, core, headSha, await evaluate(github, context, pull));
	} catch (error) {
		const reason = error instanceof Error ? error.message : String(error);
		// A policy that cannot be evaluated is broken rather than waiting, and says so in red. If
		// even that cannot be published the context stays unreported, which leaves the pull request
		// blocked on a context nothing has answered — still not mergeable.
		try {
			if (headSha) {
				await publish(github, context, core, headSha, {
					kind: "misconfigured",
					title: "The review policy could not be evaluated",
					summary: `Re-run the \`Review policy\` workflow. GitHub reported: ${reason}`,
				});
			}
		} catch {
			core.info("The review policy verdict could not be published as a check run.");
		}
		core.setFailed(`Review policy evaluation failed: ${reason}`);
	}
};
