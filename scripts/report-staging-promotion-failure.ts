import { requiredEnv } from "./lib/env.ts";
/**
 * Keeps one open issue for failed automatic staging promotions. Continuous staging dispatches each
 * promotion with `GITHUB_TOKEN`, so GitHub notifies nobody when the run fails; an issue notifies the
 * repository's watchers. A maintainer closes it after triage, and the next failure opens a new one.
 */
import { asArray, asRecord, asString, parseJson } from "./lib/json.ts";
import { output, run } from "./lib/process.ts";

const KEY = "hephaestus:staging-promotion-failure";
export const MARKER = `<!-- ${KEY} -->`;
export const TITLE = "Staging did not follow main";

export type FailureReport =
	| { kind: "create"; title: string; body: string }
	| { kind: "comment"; issue: number; body: string };

export function planFailureReport(
	open: readonly { number: number; body: string }[],
	commit: string,
	runUrl: string,
): FailureReport {
	const line = `The automatic promotion of \`${commit}\` failed: ${runUrl}`;
	const tracking = open.find((issue) => issue.body.startsWith(MARKER));
	if (tracking !== undefined) {
		return { kind: "comment", issue: tracking.number, body: line };
	}
	return {
		kind: "create",
		title: TITLE,
		body: [
			MARKER,
			line,
			"",
			"Staging keeps what it last applied. The run's summary and the Staging deployment record say which step failed.",
			"Later failures comment here. Close this issue after triage; keep the first line so failures find it.",
		].join("\n"),
	};
}

if (import.meta.main) {
	const repository = requiredEnv(process.env, "GITHUB_REPOSITORY");
	const listed = asArray(
		parseJson(
			await output("gh", [
				"issue",
				"list",
				"--repo",
				repository,
				"--state",
				"open",
				"--search",
				`"${KEY}" in:body`,
				"--json",
				"body,number",
				"--limit",
				"100",
			]),
		),
		"gh issue list",
	).map((value, index) => {
		const issue = asRecord(value, `gh issue list[${index}]`);
		return { number: Number(issue.number), body: asString(issue.body, "issue body") };
	});
	const report = planFailureReport(
		listed,
		requiredEnv(process.env, "COMMIT"),
		requiredEnv(process.env, "RUN_URL"),
	);
	await run(
		"gh",
		report.kind === "create"
			? ["issue", "create", "--repo", repository, "--title", report.title, "--body", report.body]
			: ["issue", "comment", String(report.issue), "--repo", repository, "--body", report.body],
	);
}
