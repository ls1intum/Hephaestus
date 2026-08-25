import { readdir, readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import type { TestSummary } from "./summarize-test-results.ts";

type Metric = { name: string; value: (summary: TestSummary) => number; tolerance: number };

const metrics: Metric[] = [
	{
		name: "wall time",
		value: (summary) => summary.performance?.wallTimeSeconds ?? 0,
		tolerance: 0.2,
	},
	{ name: "summed test time", value: (summary) => summary.testTimeSeconds, tolerance: 0.15 },
	{
		name: "CPU time",
		value: (summary) => summary.performance?.cpuTimeSeconds ?? 0,
		tolerance: 0.25,
	},
	{
		name: "max RSS",
		value: (summary) => summary.performance?.maxRssKilobytes ?? 0,
		tolerance: 0.25,
	},
	{
		name: "context startup time",
		value: (summary) => summary.performance?.contextStartupSeconds ?? 0,
		tolerance: 0.15,
	},
];

const median = (values: number[]): number => {
	const sorted = values.toSorted((left, right) => left - right);
	const middle = Math.floor(sorted.length / 2);
	if (sorted.length === 0) return 0;
	const upper = sorted[middle] ?? 0;
	return sorted.length % 2 === 0 ? ((sorted[middle - 1] ?? 0) + upper) / 2 : upper;
};

export function regressions(current: TestSummary, history: TestSummary[]): string[] {
	const failures: string[] = [];
	const performance = current.performance;
	if (performance === undefined) return ["performance metrics are missing"];
	if (performance.contextStarts > 15)
		failures.push(`context starts ${performance.contextStarts} > 15`);
	if (performance.contextStartupSeconds > 120) {
		failures.push(`context startup ${performance.contextStartupSeconds.toFixed(1)}s > 120s`);
	}
	const usable = history.filter((summary) => summary.performance !== undefined).slice(-8);
	if (usable.length < 3) return failures;
	for (const metric of metrics) {
		const baselineValues = usable.map(metric.value);
		const baseline = median(baselineValues);
		const deviation = median(baselineValues.map((value) => Math.abs(value - baseline)));
		const limit = baseline + Math.max(baseline * metric.tolerance, deviation * 3);
		const value = metric.value(current);
		if (value > limit)
			failures.push(`${metric.name} ${value.toFixed(1)} > variance limit ${limit.toFixed(1)}`);
	}
	return failures;
}

function parseSummary(json: string): TestSummary {
	const value: unknown = JSON.parse(json);
	const isRecord = (candidate: unknown): candidate is Record<string, unknown> =>
		typeof candidate === "object" && candidate !== null && !Array.isArray(candidate);
	const number = (record: Record<string, unknown>, key: string): number => {
		const candidate = record[key];
		if (typeof candidate !== "number") throw new Error(`Invalid CI metrics field: ${key}`);
		return candidate;
	};
	if (
		!isRecord(value) ||
		value.schemaVersion !== 2 ||
		typeof value.name !== "string" ||
		!isRecord(value.performance)
	) {
		throw new Error("Invalid CI metrics summary");
	}
	return {
		schemaVersion: 2,
		name: value.name,
		files: number(value, "files"),
		tests: number(value, "tests"),
		failures: number(value, "failures"),
		errors: number(value, "errors"),
		skipped: number(value, "skipped"),
		testTimeSeconds: number(value, "testTimeSeconds"),
		slowest: [],
		performance: {
			wallTimeSeconds: number(value.performance, "wallTimeSeconds"),
			cpuTimeSeconds: number(value.performance, "cpuTimeSeconds"),
			maxRssKilobytes: number(value.performance, "maxRssKilobytes"),
			contextStarts: number(value.performance, "contextStarts"),
			contextStartupSeconds: number(value.performance, "contextStartupSeconds"),
			contextCacheMisses: number(value.performance, "contextCacheMisses"),
		},
	};
}

async function main(): Promise<void> {
	const [currentPath, historyDirectory] = process.argv.slice(2);
	if (currentPath === undefined || historyDirectory === undefined) {
		throw new Error("Usage: check-ci-performance <current-json> <history-directory>");
	}
	const current = parseSummary(await readFile(currentPath, "utf8"));
	let files: string[] = [];
	try {
		files = (await readdir(historyDirectory)).filter((file) => file.endsWith(".json")).toSorted();
	} catch (error) {
		if (!(error instanceof Error && "code" in error && error.code === "ENOENT")) throw error;
	}
	const history = await Promise.all(
		files.map(async (file) =>
			parseSummary(await readFile(resolve(historyDirectory, file), "utf8")),
		),
	);
	const failures = regressions(current, history);
	if (failures.length > 0)
		throw new Error(`CI performance regression:\n- ${failures.join("\n- ")}`);
}

if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href)
	await main();
