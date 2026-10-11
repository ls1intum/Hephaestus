/**
 * Stage practice scripts outside the sandbox, as `pi-practice-scripts.sh` stages them inside it:
 * each script is `<stage>/practices/<slug>.ts` beside a `lib` link, so its `../lib/…` imports
 * resolve, and beside the cause and the previous claim that the server stages for it.
 */
import { mkdir, mkdtemp, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { CAUSE_FILE_SUFFIX, PREVIOUS_FILE_SUFFIX } from "./lib/contract.ts";

interface StagedPracticeScripts {
	/** A new folder under the system's temporary folder that holds the stage. */
	root: string;
	/** The folder that holds `practices/` and the `lib` link. */
	stage: string;
	/** The runner's `--practices` folder. */
	practices: string;
}

/** The files beside each script. A missing key stages no file. */
export interface StagedInputs {
	/** `<slug>.cause.json` */
	cause?: unknown;
	/** `<slug>.previous.json` */
	previous?: unknown;
}

/** A review of a pull request that a person requested, with no review before it. */
export const REQUESTED_PULL_REQUEST: Required<StagedInputs> = {
	cause: {
		kind: "scm.pull_request",
		subject: { role: "AUTHOR" },
		cause: {
			type: "requested",
			origin: "MANUAL",
			state: { draftStatus: "NOT_DRAFT", state: "OPEN" },
			admittedAt: "2026-01-01T00:00:00Z",
		},
	},
	previous: null,
};

/** Stage each script source under its slug, with the same inputs beside each. */
export async function stagePracticeScripts(
	scripts: Record<string, string>,
	inputs: StagedInputs = REQUESTED_PULL_REQUEST,
): Promise<StagedPracticeScripts> {
	const root = await mkdtemp(path.join(tmpdir(), "practice-script-"));
	const stage = path.join(root, "stage");
	const practices = path.join(stage, "practices");
	await mkdir(practices, { recursive: true });
	await writeFile(path.join(root, "package.json"), '{"type":"module"}\n');
	await symlink(path.join(import.meta.dirname, "lib"), path.join(stage, "lib"));
	for (const [slug, source] of Object.entries(scripts)) {
		await writeFile(path.join(practices, `${slug}.ts`), source);
		if ("cause" in inputs) {
			await writeFile(
				path.join(practices, `${slug}${CAUSE_FILE_SUFFIX}`),
				JSON.stringify(inputs.cause),
			);
		}
		if ("previous" in inputs) {
			await writeFile(
				path.join(practices, `${slug}${PREVIOUS_FILE_SUFFIX}`),
				JSON.stringify(inputs.previous),
			);
		}
	}
	return { root, stage, practices };
}
