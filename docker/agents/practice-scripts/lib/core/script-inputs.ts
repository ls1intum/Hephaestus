import { readFile } from "node:fs/promises";
import path from "node:path";
import { INPUTS_FILE_SUFFIX, practiceInputsSchema, type PracticeInputs } from "./contract.ts";

export type ScriptInputs = { inputs: PracticeInputs } | { error: string };
export async function readJson(file: string): Promise<{ value: unknown } | { error: string }> {
	try {
		const value: unknown = JSON.parse(await readFile(file, "utf8"));
		return { value };
	} catch (error) {
		return {
			error: `${path.basename(file)}: ${error instanceof Error ? error.message : String(error)}`,
		};
	}
}
export function parseInputs(slug: string, value: unknown): ScriptInputs {
	const parsed = practiceInputsSchema.safeParse(value);
	if (parsed.success) {
		if (parsed.data.practice.slug !== slug) {
			return { error: `${slug}${INPUTS_FILE_SUFFIX}: practice slug differs from assignment` };
		}
		return { inputs: parsed.data };
	}
	const issue = parsed.error.issues[0];
	return {
		error: `${slug}${INPUTS_FILE_SUFFIX}: ${issue?.path.join(".") ?? ""} ${issue?.message ?? "invalid"}`,
	};
}
export class InputReader {
	readonly #practicesDir: string;
	constructor(options: { practicesDir: string }) {
		this.#practicesDir = options.practicesDir;
	}
	async read(slug: string): Promise<ScriptInputs> {
		const value = await readJson(path.join(this.#practicesDir, `${slug}${INPUTS_FILE_SUFFIX}`));
		return "error" in value ? value : parseInputs(slug, value.value);
	}
}
