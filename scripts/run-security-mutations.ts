import { spawnSync } from "node:child_process";
import { appendFileSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { XMLParser } from "fast-xml-parser";
import { SyntaxValidator } from "fast-xml-validator";

const VALID_STATUSES = new Set(["KILLED", "SURVIVED", "NO_COVERAGE", "EQUIVALENT"]);
const REPORTED_STATUSES = [
	"KILLED",
	"SURVIVED",
	"NO_COVERAGE",
	"EQUIVALENT",
	"RUN_ERROR",
	"TIMED_OUT",
	"MEMORY_ERROR",
	"NON_VIABLE",
	"NOT_STARTED",
	"STARTED",
] as const;

type Summary = {
	actionable: MutationDetail[];
	counts: Map<string, number>;
	error?: string;
	total: number;
	valid: boolean;
};

type MutationDetail = {
	className: string;
	description: string;
	line: string;
	method: string;
	status: string;
};

const parser = new XMLParser({ ignoreAttributes: false, attributeNamePrefix: "" });

export function summarizePitXml(xml: string): Summary {
	try {
		SyntaxValidator.validate(xml);
	} catch (error) {
		return invalidSummary(
			`invalid XML: ${error instanceof Error ? error.message : "unknown error"}`,
		);
	}
	const document: unknown = parser.parse(xml);
	const root = isRecord(document) ? document.mutations : undefined;
	const rawMutations = isRecord(root) ? root.mutation : undefined;
	if (rawMutations === undefined) return invalidSummary("report contains no mutations");
	if (!isRecord(rawMutations) && !isRecordArray(rawMutations)) {
		return invalidSummary("report contains a malformed mutation entry");
	}
	const mutations = isRecordArray(rawMutations) ? rawMutations : [rawMutations];
	const counts = new Map<string, number>();
	const actionable: MutationDetail[] = [];
	for (const mutation of mutations) {
		const status = typeof mutation.status === "string" ? mutation.status : "INVALID";
		counts.set(status, (counts.get(status) ?? 0) + 1);
		if (status === "SURVIVED" || status === "NO_COVERAGE") {
			actionable.push({
				className: textField(mutation.mutatedClass),
				description: textField(mutation.description),
				line: textField(mutation.lineNumber),
				method: textField(mutation.mutatedMethod),
				status,
			});
		}
	}
	const unexpected = [...counts.keys()].filter((status) => !VALID_STATUSES.has(status));
	return {
		actionable,
		counts,
		error:
			unexpected.length > 0 ? `unexpected mutation status: ${unexpected.join(", ")}` : undefined,
		total: mutations.length,
		valid: mutations.length > 0 && unexpected.length === 0,
	};
}

function invalidSummary(error: string): Summary {
	return { actionable: [], counts: new Map(), error, total: 0, valid: false };
}

function textField(value: unknown): string {
	return typeof value === "string" || typeof value === "number" ? String(value) : "?";
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isRecordArray(value: unknown): value is Record<string, unknown>[] {
	return Array.isArray(value) && value.every(isRecord);
}

function runMaven(server: string, args: string[]): { exitCode: number; seconds: number } {
	const started = performance.now();
	const result = spawnSync("./mvnw", args, { cwd: server, stdio: "inherit" });
	return {
		exitCode: result.status ?? 1,
		seconds: Math.round((performance.now() - started) / 1000),
	};
}

function markdown(
	summary: Summary,
	setupSeconds: number,
	compileSeconds: number,
	analysisSeconds: number,
	passed: boolean,
) {
	const analyzed = summary.total > 0;
	const lines = [
		`# Security mutation testing: ${passed ? "PASS" : "FAIL"}`,
		"",
		"| Metric | Value |",
		"| --- | ---: |",
		`| Dependency setup | ${setupSeconds}s |`,
		`| Preflight compilation | ${compileSeconds}s |`,
		`| PIT goal wall time | ${analysisSeconds}s |`,
		`| Generated mutants | ${analyzed ? summary.total : "N/A"} |`,
		...REPORTED_STATUSES.map(
			(status) => `| ${status} | ${analyzed ? (summary.counts.get(status) ?? 0) : "N/A"} |`,
		),
		"",
		passed
			? "PIT completed without technical analysis errors. Review surviving and uncovered mutations in the HTML report."
			: `The run is invalid: ${summary.error ?? "Maven or PIT failed"}. Do not interpret its mutation score.`,
		"",
	];
	if (summary.actionable.length > 0) {
		lines.push(
			"## Mutations to review",
			"",
			"| Status | Location | Mutation |",
			"| --- | --- | --- |",
			...summary.actionable.map(
				(item) =>
					`| ${item.status} | ${markdownCell(item.className)}.${markdownCell(item.method)}:${markdownCell(item.line)} | ${markdownCell(item.description)} |`,
			),
			"",
		);
	}
	return lines.join("\n");
}

function markdownCell(value: string): string {
	return value.replaceAll("|", "\\|").replaceAll(/[\r\n]+/g, " ");
}

function main() {
	const repo = resolve(import.meta.dirname, "..");
	const server = resolve(repo, "server");
	const reportDirectory = resolve(server, "application/target/pit-reports");
	const xmlPath = resolve(reportDirectory, "mutations.xml");
	rmSync(reportDirectory, { recursive: true, force: true });
	mkdirSync(reportDirectory, { recursive: true });

	const common = ["-f", "application/pom.xml", "-Ppitest", "-Dmaven.build.cache.enabled=false"];
	const setup = runMaven(server, [
		"-pl",
		"generated-clients",
		"-am",
		"install",
		"-DskipTests",
		"--batch-mode",
	]);
	const compilation =
		setup.exitCode === 0
			? runMaven(server, [...common, "-DskipTests", "test-compile", "--batch-mode"])
			: { exitCode: 1, seconds: 0 };
	const analysis =
		compilation.exitCode === 0
			? runMaven(server, [...common, "org.pitest:pitest-maven:mutationCoverage", "--batch-mode"])
			: { exitCode: 1, seconds: 0 };

	let summary = invalidSummary(
		setup.exitCode !== 0
			? `dependency setup failed (Maven exit ${setup.exitCode})`
			: compilation.exitCode !== 0
				? `preflight compilation failed (Maven exit ${compilation.exitCode})`
				: analysis.exitCode !== 0
					? `PIT goal failed (Maven exit ${analysis.exitCode})`
					: "mutation report was not produced",
	);
	if (setup.exitCode === 0 && compilation.exitCode === 0 && analysis.exitCode === 0) {
		try {
			summary = summarizePitXml(readFileSync(xmlPath, "utf8"));
		} catch (error) {
			summary = invalidSummary(
				error instanceof Error ? error.message : "mutation report could not be read",
			);
		}
	}
	const passed =
		setup.exitCode === 0 && compilation.exitCode === 0 && analysis.exitCode === 0 && summary.valid;
	const output = markdown(summary, setup.seconds, compilation.seconds, analysis.seconds, passed);
	writeFileSync(resolve(reportDirectory, "summary.md"), output);
	process.stdout.write(output);
	if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, output);
	if (!passed) process.exitCode = 1;
}

if (process.argv[1] && resolve(process.argv[1]) === import.meta.filename) main();
