/**
 * Stage practice programs outside the sandbox, as the server and the runtime stage them inside it:
 * each program is `<stage>/practices/<slug>.ts` beside a `lib` link, so its `../lib/…` imports
 * resolve, and beside the cause and the previous result that the server stages for it.
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
	/** The folder of the staged programs and their inputs: `task.json#practiceScripts`. */
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
		practice: { revisionId: 1 },
		scope: { key: "scm.pull_request:1:AUTHOR", mode: "change", description: "the reviewed change" },
		cause: {
			type: "requested",
			origin: "MANUAL",
			state: { draftStatus: "NOT_DRAFT", state: "OPEN" },
			admittedAt: "2026-01-01T00:00:00Z",
		},
	},
	previous: null,
};

/** Stage each program source under its slug, with the same inputs beside each. A null source stages the inputs only: the built-in. */
export async function stagePracticeScripts(
	scripts: Record<string, string | null>,
	inputs: StagedInputs = REQUESTED_PULL_REQUEST,
): Promise<StagedPracticeScripts> {
	const root = await mkdtemp(path.join(tmpdir(), "practice-script-"));
	const stage = path.join(root, "stage");
	const practices = path.join(stage, "practices");
	await mkdir(practices, { recursive: true });
	await writeFile(path.join(root, "package.json"), '{"type":"module"}\n');
	await symlink(path.join(import.meta.dirname, "lib"), path.join(stage, "lib"));
	for (const [slug, source] of Object.entries(scripts)) {
		if (source !== null) {
			await writeFile(path.join(practices, `${slug}.ts`), source);
		}
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
