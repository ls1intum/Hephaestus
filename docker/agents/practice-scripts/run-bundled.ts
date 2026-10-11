/**
 * Run one bundled practice script on a pull request, and check its result as the runner checks it:
 * against the result schema, and each lead kind against `meta.kinds`.
 */
import { rm } from "node:fs/promises";

import { practiceScriptResultSchema, type PracticeScriptResult } from "./lib/contract.ts";
import {
	loadDefinition,
	pullRequestRecord,
	runDefinition,
	type RunInputs,
} from "./run-definition.ts";

/** `RunInputs` with the fields of the pull request record in place of the whole record. */
export type PullRequestInputs = Omit<RunInputs, "work"> & { work?: Record<string, unknown> };

export async function runBundled(
	slug: string,
	inputs: PullRequestInputs = {},
): Promise<PracticeScriptResult> {
	const { definition, root } = await loadDefinition(slug);
	try {
		const result = practiceScriptResultSchema.parse(
			await runDefinition(definition, { ...inputs, work: pullRequestRecord(inputs.work) }),
		);
		const undeclared = result.leads.filter(
			(lead) => !Object.hasOwn(definition.meta.kinds, lead.kind),
		);
		if (undeclared.length > 0) {
			throw new Error(
				`${slug} returned undeclared lead kinds: ${undeclared.map((lead) => lead.kind).join(", ")}`,
			);
		}
		return result;
	} finally {
		await rm(root, { recursive: true, force: true });
	}
}
