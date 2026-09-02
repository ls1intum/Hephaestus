/**
 * Runs CI/CD on the Version PR's branch.
 *
 * `changesets/action` pushes the Version PR with `GITHUB_TOKEN`, and a push made with that token
 * starts no workflow run. The conclusion drawn from that was that the Version PR cannot be checked
 * at all, so it merged through a standing ruleset bypass — leaving the one commit whose merge cuts a
 * release as the one commit no gate had looked at before it landed.
 *
 * The premise is wrong in one specific way. `workflow_dispatch` and `repository_dispatch` are the
 * two documented exceptions to the no-new-run rule, so the same `GITHUB_TOKEN` can start the same
 * CI/CD workflow on the same branch. The run reports its `All CI Passed` commit status onto the
 * branch head, which is the Version PR's head commit, so the Version PR can carry required checks
 * like any other and needs no bypass. No app, no personal access token, no long-lived credential.
 *
 * The rule is one line: every Version PR head commit gets a CI/CD run. Asking whether one already
 * exists, rather than tracking what changed, makes a missed dispatch self-healing on the next push
 * to `main` and makes a repeat push that changed nothing free.
 */
import { asArray, asRecord, asString, readJsonFile } from "./lib/json.ts";
import { output } from "./lib/process.ts";

/** The CI/CD workflow the Version PR must pass, by file name, as `gh workflow run` names it. */
export const CI_WORKFLOW = "cicd.yml";

/**
 * The branch `changesets/action` maintains the Version PR on. Changesets derives it from the
 * configured base branch, so this reads the same `.changeset/config.json` rather than restating the
 * name — a base-branch rename must not silently stop validating releases.
 */
export function versionBranch(config: unknown): string {
	const baseBranch = asString(asRecord(config, "changeset config").baseBranch, "baseBranch");
	if (!baseBranch) throw new Error("changeset config declares no baseBranch");
	return `changeset-release/${baseBranch}`;
}

export interface WorkflowRun {
	readonly headSha: string;
}

/** Every Version PR head commit gets a run; a head that already has one is already covered. */
export function needsDispatch(headSha: string, runs: readonly WorkflowRun[]): boolean {
	return !runs.some((run) => run.headSha === headSha);
}

export function parseRuns(value: unknown): WorkflowRun[] {
	return asArray(asRecord(value, "workflow runs").workflow_runs, "workflow_runs").map(
		(run, index) => ({
			headSha: asString(asRecord(run, `run ${index}`).head_sha, `run ${index} head_sha`),
		}),
	);
}

async function gh(args: string[]): Promise<string> {
	return output("gh", args);
}

async function branchHead(repository: string, branch: string): Promise<string | undefined> {
	try {
		return asString(
			asRecord(
				asRecord(JSON.parse(await gh(["api", `repos/${repository}/branches/${branch}`])), "branch")
					.commit,
				"branch commit",
			).sha,
			"branch commit sha",
		);
	} catch {
		// No Version PR branch: there are no pending changesets, so there is nothing to validate.
		return undefined;
	}
}

if (import.meta.main) {
	const repository = process.env.GITHUB_REPOSITORY;
	if (!repository) throw new Error("GITHUB_REPOSITORY is required");
	const branch = versionBranch(await readJsonFile(".changeset/config.json"));
	const headSha = await branchHead(repository, branch);
	if (!headSha) {
		process.stdout.write(`No ${branch} branch; nothing to validate.\n`);
	} else {
		const runs = parseRuns(
			JSON.parse(
				await gh([
					"api",
					`repos/${repository}/actions/workflows/${CI_WORKFLOW}/runs?branch=${encodeURIComponent(branch)}&per_page=100`,
				]),
			),
		);
		if (needsDispatch(headSha, runs)) {
			// `release-preflight` turns on the evidence gate's own checks — SBOM, licence,
			// vulnerability policy on both platforms, index membership — against the images this run
			// builds, so the Version PR proves what the release would otherwise discover first.
			await gh(["workflow", "run", CI_WORKFLOW, "--ref", branch, "-f", "release-preflight=true"]);
			process.stdout.write(`Dispatched CI/CD on ${branch} at ${headSha}.\n`);
		} else {
			process.stdout.write(`CI/CD already ran for ${branch} at ${headSha}.\n`);
		}
	}
}
