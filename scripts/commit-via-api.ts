/**
 * Commits the work tree to a branch through GitHub's `createCommitOnBranch` mutation.
 *
 * A ruleset that requires signed commits rejects everything `git commit` produces inside a workflow:
 * Actions holds no signing key and GitHub will not vouch for a commit it did not make. The mutation
 * is the path GitHub signs — it is how `changesets/action` lands the Version PR and how Renovate
 * lands a dependency bump, and both verify as `web-flow`. The credential is unchanged, so the
 * documented consequence of committing with `GITHUB_TOKEN` is unchanged too: the resulting push
 * starts no further workflow run.
 *
 * The mutation carries file contents rather than a diff, so every path the commit touches has to be
 * named and every added or modified file sent whole. A rename has no representation of its own: it
 * is the source path deleted and the destination path added.
 *
 * `expectedHeadOid` is what the shell path spelled as "never force-push". A branch that moved while
 * the files were being produced rejects the commit instead of burying the move.
 */
import { appendFile, readFile } from "node:fs/promises";
import { parseArgs } from "node:util";

import { requiredEnv } from "./lib/env.ts";
import { asRecord, asString, at, isRecord } from "./lib/json.ts";
import { output } from "./lib/process.ts";

/** Paths whose work-tree state one commit must carry, as `createCommitOnBranch` divides them. */
export interface WorkTreeChanges {
	readonly additions: readonly string[];
	readonly deletions: readonly string[];
}

/** `CreateCommitOnBranchInput`, restricted to the fields a workflow commit uses. */
export interface CommitInput {
	readonly branch: { readonly repositoryNameWithOwner: string; readonly branchName: string };
	readonly expectedHeadOid: string;
	readonly message: { readonly headline: string };
	readonly fileChanges: {
		readonly additions: readonly { readonly path: string; readonly contents: string }[];
		readonly deletions: readonly { readonly path: string }[];
	};
}

export interface CommitTarget {
	readonly repository: string;
	readonly branch: string;
	readonly expectedHeadOid: string;
	readonly headline: string;
}

/** The two-letter codes `git status` uses for a path it cannot describe as one change. */
const UNMERGED = new Set(["DD", "AU", "UD", "UA", "DU", "AA", "UU"]);

/**
 * Reads `git status --porcelain=v1 -z`, whose entries are NUL-terminated `XY path`, and whose rename
 * and copy entries put the source path in a field of its own after the destination.
 */
export function changedPaths(porcelain: string): WorkTreeChanges {
	const fields = porcelain.split("\0");
	const additions: string[] = [];
	const deletions: string[] = [];
	for (let index = 0; index < fields.length; index += 1) {
		const entry = fields[index];
		// The last field after a trailing NUL is empty.
		if (!entry) continue;
		const codes = entry.slice(0, 2);
		const path = entry.slice(3);
		if (UNMERGED.has(codes))
			throw new Error(`${path} is unmerged; a conflicted work tree is not a commit`);
		if (codes.startsWith("R") || codes.startsWith("C")) {
			index += 1;
			const source = fields[index];
			if (!source) throw new Error(`${path} is reported as a rename with no source path`);
			// A copy leaves its source where it is; a rename does not.
			if (codes.startsWith("R")) deletions.push(source);
		}
		// The work-tree column decides wherever it has an opinion: it holds the state to be committed,
		// and the index column only says what the path looked like on the way there.
		const state = codes[1] === " " ? codes[0] : codes[1];
		(state === "D" ? deletions : additions).push(path);
	}
	return { additions: additions.toSorted(), deletions: deletions.toSorted() };
}

export function commitInput(
	target: CommitTarget,
	contents: ReadonlyMap<string, Uint8Array>,
	deletions: readonly string[],
): CommitInput {
	return {
		branch: { repositoryNameWithOwner: target.repository, branchName: target.branch },
		expectedHeadOid: target.expectedHeadOid,
		message: { headline: target.headline },
		fileChanges: {
			additions: [...contents].map(([path, bytes]) => ({
				path,
				contents: Buffer.from(bytes).toString("base64"),
			})),
			deletions: deletions.map((path) => ({ path })),
		},
	};
}

const COMMIT_MUTATION = `mutation ($input: CreateCommitOnBranchInput!) {
  createCommitOnBranch(input: $input) {
    commit {
      oid
      signature {
        state
        wasSignedByGitHub
      }
    }
  }
}`;

/** The new commit's object name and what GitHub says about its signature. */
async function createCommit(token: string, input: CommitInput): Promise<string> {
	const response = await fetch(process.env.GITHUB_GRAPHQL_URL ?? "https://api.github.com/graphql", {
		method: "POST",
		headers: { authorization: `bearer ${token}`, "content-type": "application/json" },
		body: JSON.stringify({ query: COMMIT_MUTATION, variables: { input } }),
	});
	const text = await response.text();
	if (!response.ok) throw new Error(`GitHub answered ${response.status} ${response.statusText}`);
	const body = asRecord(JSON.parse(text), "GraphQL response");
	// A moved head, a deletion of a path the branch does not have, a branch that is not a branch:
	// GitHub explains each of them here, and none of the explanations quote the credential.
	if (body.errors) throw new Error(`createCommitOnBranch refused the commit: ${text}`);
	const commit = asRecord(
		at(body, ["data", "createCommitOnBranch", "commit"], "response"),
		"commit",
	);
	const signature = isRecord(commit.signature) ? commit.signature : undefined;
	const verification = signature
		? `${asString(signature.state, "signature state")}, signed by GitHub: ${signature.wasSignedByGitHub === true}`
		: "unsigned";
	return `${asString(commit.oid, "commit oid")} (${verification})`;
}

if (import.meta.main) {
	const { values, positionals } = parseArgs({
		options: {
			branch: { type: "string" },
			message: { type: "string" },
			"expected-head": { type: "string" },
		},
		allowPositionals: true,
	});
	const { branch, message } = values;
	if (!branch || !message) throw new Error("--branch and --message are required");
	const target: CommitTarget = {
		repository: requiredEnv(process.env, "GITHUB_REPOSITORY"),
		branch,
		headline: message,
		// The work tree was compared against this commit, so nothing else is a base the changes below
		// describe.
		expectedHeadOid: values["expected-head"] ?? (await output("git", ["rev-parse", "HEAD"])).trim(),
	};
	const changes = changedPaths(
		await output("git", [
			"status",
			"--porcelain=v1",
			"-z",
			"--untracked-files=all",
			...(positionals.length > 0 ? ["--", ...positionals] : []),
		]),
	);
	const changed = changes.additions.length > 0 || changes.deletions.length > 0;
	if (!changed) {
		console.log("The work tree matches the branch; no commit was created.");
	} else {
		const contents = new Map(
			await Promise.all(
				changes.additions.map(async (path) => [path, await readFile(path)] as const),
			),
		);
		const token = requiredEnv(process.env, "GH_TOKEN");
		console.log(
			`Committed ${await createCommit(token, commitInput(target, contents, changes.deletions))} on ${branch}.`,
		);
	}
	if (process.env.GITHUB_OUTPUT)
		await appendFile(process.env.GITHUB_OUTPUT, `changed=${changed}\n`);
}
