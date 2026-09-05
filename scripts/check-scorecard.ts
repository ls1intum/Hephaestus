import { appendFile, mkdir, writeFile } from "node:fs/promises";

import { asArray, asRecord, asString, readJsonFile } from "./lib/json.ts";

function scores(value: unknown): Map<string, number> {
	const result = new Map<string, number>();
	for (const item of asArray(value, "checks")) {
		const check = asRecord(item, "check");
		const name = asString(check.name, "check.name");
		const score = check.score;
		if (
			!name ||
			result.has(name) ||
			typeof score !== "number" ||
			!Number.isInteger(score) ||
			score < -1 ||
			score > 10
		)
			throw new Error(`Invalid or duplicate check: ${name}`);
		result.set(name, score);
	}
	return result;
}

export function checkScorecard(
	baselineValue: unknown,
	assessmentValue: unknown,
	now = Date.now(),
): string[] {
	const baseline = asRecord(baselineValue, "baseline");
	const assessment = asRecord(assessmentValue, "assessment");
	const repository = asString(baseline.repository, "baseline.repository");
	if (asRecord(assessment.repo, "repo").name !== repository)
		throw new Error("Assessment belongs to a different repository");
	const date = Date.parse(asString(assessment.date, "assessment.date"));
	// The published assessment refreshes on each push to main; eight days tolerates a quiet week.
	if (!Number.isFinite(date) || date > now + 5 * 60_000 || now - date > 8 * 86_400_000)
		throw new Error("Assessment is stale or has an invalid/future date");
	const baselineDate = Date.parse(asString(baseline.date, "baseline.date"));
	if (!Number.isFinite(baselineDate)) throw new Error("Invalid baseline date");
	if (date < baselineDate) throw new Error("Assessment predates the baseline");
	const expected = scores(baseline.checks);
	const actual = scores(assessment.checks);
	const excluded = asRecord(baseline.excluded, "baseline.excluded");
	for (const [name, reason] of Object.entries(excluded)) {
		if (!expected.has(name) || !asString(reason, `exclusion ${name}`).trim())
			throw new Error(`Invalid exclusion: ${name}`);
	}
	const enforced = [...expected].filter(([name]) => !Object.hasOwn(excluded, name));
	if (enforced.length === 0 || enforced.some(([, minimum]) => minimum < 0))
		throw new Error("Baseline must enforce at least one check with nonnegative minimums");
	const failures: string[] = [];
	for (const [name, minimum] of enforced) {
		const current = actual.get(name);
		if (current === undefined || current < minimum)
			failures.push(`${name}: ${current ?? "missing"} (minimum ${minimum})`);
	}
	return failures;
}

async function main() {
	const baseline = asRecord(await readJsonFile("security/scorecard-baseline.json"), "baseline");
	const repository = asString(baseline.repository, "baseline.repository");
	const response = await fetch(`https://api.scorecard.dev/projects/${repository}`, {
		signal: AbortSignal.timeout(30_000),
	});
	if (!response.ok) throw new Error(`Scorecard API returned HTTP ${response.status}`);
	const assessment: unknown = await response.json();
	await mkdir("tmp", { recursive: true });
	await writeFile("tmp/scorecard-assessment.json", `${JSON.stringify(assessment, null, 2)}\n`);
	const failures = checkScorecard(baseline, assessment);
	const summary = `## Scorecard ratchet\n\n${failures.length ? failures.map((failure) => `- ${failure}`).join("\n") : "All enforced checks meet the committed baseline."}\n`;
	process.stdout.write(summary);
	if (process.env.GITHUB_STEP_SUMMARY) await appendFile(process.env.GITHUB_STEP_SUMMARY, summary);
	if (failures.length) process.exitCode = 1;
}

if (import.meta.main) await main();
