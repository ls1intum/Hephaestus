import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { afterEach, beforeEach, describe, it } from "node:test";

import {
	CHECK_NAME,
	currentApprovers,
	decide,
	enforce,
	type ActionsContext,
	type CheckRunRequest,
	type GitHubApi,
	type PullRequest,
	type Review,
	parseMaintainers,
} from "./review-policy.ts";

const originalEnvironment = { ...process.env };

const HEAD = "0123456789abcdef0123456789abcdef01234567";
const OTHER = "fedcba9876543210fedcba9876543210fedcba98";

const review = (over: Partial<Review> & Pick<Review, "id">): Review => ({
	state: "APPROVED",
	commit_id: HEAD,
	user: { login: "reviewer" },
	...over,
});

const pull: PullRequest = {
	number: 7,
	base: { ref: "main" },
	head: { sha: HEAD },
	user: { login: "Contributor" },
};

const makeCore = () => {
	const infos: string[] = [];
	const failures: string[] = [];
	return {
		infos,
		failures,
		info: (message: string) => infos.push(message),
		setFailed: (message: string) => failures.push(message),
	};
};

interface GitHubOptions {
	readonly resolvedPull?: PullRequest;
	readonly reviews?: readonly Review[];
	readonly permissions?: Readonly<Record<string, string>>;
	readonly failPullWith?: Error;
}

const makeGitHub = (options: GitHubOptions = {}) => {
	const created: CheckRunRequest[] = [];
	const reviews = [...(options.reviews ?? [])];
	const getPull = () =>
		options.failPullWith
			? Promise.reject(options.failPullWith)
			: Promise.resolve({ data: options.resolvedPull ?? pull });

	const github: GitHubApi = {
		paginate: () => Promise.resolve(reviews),
		rest: {
			checks: {
				create: (params) => {
					created.push(params);
					return Promise.resolve({ data: { id: created.length } });
				},
			},
			pulls: {
				get: getPull,
				listReviews: () => Promise.resolve({ data: reviews }),
			},
			repos: {
				getCollaboratorPermissionLevel: (params) =>
					Promise.resolve({
						data: { permission: options.permissions?.[String(params.username)] ?? "read" },
					}),
			},
		},
	};
	return { created, github };
};

/** The single check run a run under test is expected to have published. */
const onlyRun = (created: readonly CheckRunRequest[]): CheckRunRequest => {
	assert.equal(created.length, 1);
	const [run] = created;
	assert.ok(run);
	return run;
};

const context: ActionsContext = {
	eventName: "pull_request_target",
	repo: { owner: "owner", repo: "repo" },
	payload: { pull_request: { number: 7, head: { sha: HEAD } } },
};

void describe("review policy", () => {
	beforeEach(() => {
		process.env.MAINTAINERS = "Maintainer";
		delete process.env.GITHUB_RUN_ID;
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

	void describe("currentApprovers", () => {
		void it("accepts an approval of the head commit from a non-author", () => {
			assert.deepEqual(currentApprovers([review({ id: 1 })], "contributor", HEAD), ["reviewer"]);
		});

		void it("ignores the author's own approval", () => {
			const own = review({ id: 1, user: { login: "Contributor" } });
			assert.deepEqual(currentApprovers([own], "contributor", HEAD), []);
		});

		void it("ignores an approval of an earlier commit", () => {
			const stale = [review({ id: 1, commit_id: OTHER })];
			assert.deepEqual(currentApprovers(stale, "contributor", HEAD), []);
		});

		void it("keeps only a reviewer's latest decisive review", () => {
			const reviews = [
				review({ id: 1 }),
				review({ id: 2, state: "CHANGES_REQUESTED" }),
				review({ id: 9, state: "COMMENTED" }),
			];
			assert.deepEqual(currentApprovers(reviews, "contributor", HEAD), []);
		});

		void it("lets a later approval replace requested changes, whatever order they arrive in", () => {
			const reviews = [review({ id: 5 }), review({ id: 2, state: "CHANGES_REQUESTED" })];
			assert.deepEqual(currentApprovers(reviews, "contributor", HEAD), ["reviewer"]);
		});

		void it("treats a dismissed approval as no longer standing", () => {
			const reviews = [review({ id: 1 }), review({ id: 2, state: "DISMISSED" })];
			assert.deepEqual(currentApprovers(reviews, "contributor", HEAD), []);
		});

		void it("survives a review whose author is gone", () => {
			assert.deepEqual(currentApprovers([review({ id: 1, user: null })], "contributor", HEAD), []);
		});
	});

	void describe("decide", () => {
		const base = {
			author: "Contributor",
			headSha: HEAD,
			reviews: [] as readonly Review[],
			permissionOf: () => Promise.resolve("read"),
		};

		void it("passes a listed maintainer without any approval", async () => {
			const verdict = await decide({
				...base,
				author: "MaIntAiner",
				maintainers: new Set(["maintainer"]),
			});
			assert.equal(verdict.kind, "satisfied");
			assert.match(verdict.title, /is a listed maintainer/);
		});

		void it("passes an approval from a non-author with write access", async () => {
			const verdict = await decide({
				...base,
				maintainers: new Set(["maintainer"]),
				reviews: [review({ id: 1 })],
				permissionOf: () => Promise.resolve("write"),
			});
			assert.equal(verdict.kind, "satisfied");
			assert.equal(verdict.title, "Approved by @reviewer");
		});

		void it("accepts admin and maintain access too", async () => {
			for (const permission of ["admin", "maintain"]) {
				const verdict = await decide({
					...base,
					maintainers: new Set(["maintainer"]),
					reviews: [review({ id: 1 })],
					permissionOf: () => Promise.resolve(permission),
				});
				assert.equal(verdict.kind, "satisfied", permission);
			}
		});

		void it("waits when the only approver lacks write access", async () => {
			const verdict = await decide({
				...base,
				maintainers: new Set(["maintainer"]),
				reviews: [review({ id: 1 })],
			});
			assert.equal(verdict.kind, "waiting");
			assert.equal(verdict.title, "Waiting for a maintainer approval");
		});

		void it("tells the reader what unblocks the pull request", async () => {
			const verdict = await decide({ ...base, maintainers: new Set(["maintainer"]) });
			assert.match(verdict.summary, /waiting, not broken/);
			assert.match(verdict.summary, /other than the author/);
			assert.match(verdict.summary, /0123456/);
		});

		void it("reports an empty allow-list as a misconfiguration, not as waiting", async () => {
			const verdict = await decide({ ...base, maintainers: new Set() });
			assert.equal(verdict.kind, "misconfigured");
			assert.match(verdict.title, /REVIEW_POLICY_MAINTAINERS/);
		});

		void it("still passes a valid approval when the allow-list is empty", async () => {
			const verdict = await decide({
				...base,
				maintainers: new Set(),
				reviews: [review({ id: 1 })],
				permissionOf: () => Promise.resolve("write"),
			});
			assert.equal(verdict.kind, "satisfied");
		});
	});

	void describe("enforce", () => {
		void it("publishes a pending check run while an approval is outstanding", async () => {
			const { created, github } = makeGitHub();
			const core = makeCore();
			await enforce({ github, context, core });

			const run = onlyRun(created);
			assert.equal(run.name, CHECK_NAME);
			assert.equal(run.head_sha, HEAD);
			// Pending blocks the merge button and merge-queue entry. `neutral` and `skipped` are
			// documented as passing, so neither may ever stand in for waiting.
			assert.equal(run.status, "in_progress");
			assert.equal(run.conclusion, undefined);
			assert.deepEqual(core.failures, []);
		});

		void it("publishes success once a write-access reviewer has approved the head commit", async () => {
			const { created, github } = makeGitHub({
				reviews: [review({ id: 1 })],
				permissions: { reviewer: "write" },
			});
			const core = makeCore();
			await enforce({ github, context, core });

			const run = onlyRun(created);
			assert.equal(run.status, "completed");
			assert.equal(run.conclusion, "success");
			assert.deepEqual(core.failures, []);
		});

		void it("stays out of the way of a pull request that does not target main", async () => {
			const { created, github } = makeGitHub({
				resolvedPull: { ...pull, base: { ref: "release" } },
			});
			const core = makeCore();
			await enforce({ github, context, core });

			const run = onlyRun(created);
			assert.equal(run.conclusion, "success");
			assert.match(run.output.title, /release/);
		});

		void it("reports an evaluation that could not run in red, and fails the job", async () => {
			const { created, github } = makeGitHub({ failPullWith: new Error("API is down") });
			const core = makeCore();
			await enforce({ github, context, core });

			const run = onlyRun(created);
			assert.equal(run.conclusion, "failure");
			assert.equal(run.head_sha, HEAD);
			assert.equal(core.failures.length, 1);
		});

		void it("fails loudly when an event carries no pull request at all", async () => {
			const { created, github } = makeGitHub();
			const core = makeCore();
			await enforce({
				github,
				core,
				context: { eventName: "pull_request_review", repo: context.repo, payload: {} },
			});

			assert.deepEqual(created, []);
			assert.equal(core.failures.length, 1);
		});

		void it("links the check run to the run that decided it", async () => {
			process.env.GITHUB_RUN_ID = "42";
			process.env.GITHUB_SERVER_URL = "https://github.example";
			const { created, github } = makeGitHub();
			await enforce({ github, context, core: makeCore() });

			assert.equal(
				onlyRun(created).details_url,
				"https://github.example/owner/repo/actions/runs/42",
			);
		});
	});

	// A mismatch between these and the ruleset's required context deadlocks every pull request,
	// including the one that would fix it, so the workflow's half of the contract is asserted here.
	void describe("the workflow that publishes it", () => {
		const workflow = readFileSync(
			new URL("../.github/workflows/review-policy.yml", import.meta.url),
			"utf8",
		);

		void it("publishes the merge group's verdict under the required name", () => {
			assert.match(workflow, new RegExp(`name: '${CHECK_NAME}',`));
			assert.match(workflow, /head_sha: context\.payload\.merge_group\.head_sha/);
			assert.match(workflow, /conclusion: 'success'/);
		});

		void it("answers the merge group without a checkout it may not be able to make", () => {
			// On `merge_group` the workflow comes from the queued commit while the checkout would come
			// from the default branch, so the queue must never depend on a file that has yet to land.
			const mergeGroupOnly = workflow.slice(
				workflow.indexOf("Pass the merge group"),
				workflow.indexOf("Load the trusted policy evaluator"),
			);
			assert.ok(mergeGroupOnly.length > 0);
			assert.doesNotMatch(mergeGroupOnly, /uses: actions\/checkout|GITHUB_WORKSPACE/);
		});

		void it("never names the job after the context it publishes", () => {
			// Two check runs of one name on a commit make which one the ruleset reads a race.
			assert.doesNotMatch(workflow, new RegExp(`^\\s*name: ${CHECK_NAME}\\s*$`, "m"));
		});

		void it("keeps the checkout on the base branch's copy of the policy", () => {
			assert.match(workflow, /ref: \$\{\{ github\.event\.repository\.default_branch \}\}/);
			assert.doesNotMatch(workflow, /ref: \$\{\{ github\.sha \}\}/);
		});
	});
});
