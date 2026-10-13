import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { describe, test } from "node:test";
import { isDeepStrictEqual } from "node:util";

import { z } from "zod";

import {
	CANDIDATE_LIMITS,
	CANDIDATE_REFUSALS,
	executionReportSchema,
	LEGACY_CONTRACT_ERROR,
	legacyContractError,
	MAX_REVIEW_OPTIONS_BYTES,
	parseCandidate,
	parseMeta,
	parseReviewOptions,
	practiceInputsSchema,
	type CandidateRefusal,
} from "./contract.ts";
import { isJsonObject } from "./json.ts";

/** The fixtures that the server's `CandidateFixturesTest` reads too. */
const FIXTURES = path.join(
	import.meta.dirname,
	"../../../../server/application/src/test/resources/practice-results",
);

async function fixture(name: string): Promise<unknown> {
	return JSON.parse(await readFile(path.join(FIXTURES, name), "utf8"));
}

async function fixtureObject(name: string): Promise<Record<string, unknown>> {
	const value = await fixture(name);
	assert.ok(isJsonObject(value), `${name} holds an object`);
	return value;
}

async function names(prefix: string): Promise<string[]> {
	const all = await readdir(FIXTURES);
	return all.filter((name) => name.startsWith(prefix) && name.endsWith(".json"));
}

/** The parsed candidate, or a failure that names the refusal. */
function accepted(value: unknown) {
	const parsed = parseCandidate(value);
	assert.ok(parsed.ok, parsed.ok ? "" : `${parsed.reasonCode}: ${parsed.message}`);
	return parsed.candidate;
}

function refusalOf(value: unknown): CandidateRefusal | "accepted" {
	const parsed = parseCandidate(value);
	return parsed.ok ? "accepted" : parsed.reasonCode;
}

void describe("the shared practice result fixtures", () => {
	void test("citation revisions accept exactly the server fixture's lowercase commit ids", async () => {
		const revisions = z
			.object({ valid: z.array(z.string()), invalid: z.array(z.string()) })
			.parse(await fixture("citation-revisions.json"));
		for (const revision of revisions.valid) {
			accepted({
				...notMet(),
				example: {
					citation: { ...citation(40, "  throw error;"), revision },
					note: "This catch rethrows.",
				},
			});
		}
		for (const revision of revisions.invalid) {
			assert.equal(
				refusalOf({
					...notMet(),
					example: {
						citation: { ...citation(40, "  throw error;"), revision },
						note: "This catch rethrows.",
					},
				}),
				"malformed_candidate",
			);
		}
	});
	void test("every candidate fixture parses, and keeps its content through a parse", async () => {
		const files = await names("candidate-");
		assert.equal(files.length, 4);
		for (const name of files) {
			const value = await fixture(name);
			// Strictly equal: the parse adds, drops and reorders nothing that JSON would carry.
			assert.deepStrictEqual(accepted(value), value, name);
		}
	});

	void test("the admission request holds one parsed candidate per practice, sorted by slug, each a candidate fixture", async () => {
		const request = await fixtureObject("admission-request.json");
		assert.equal(request.schemaVersion, 2);
		const { results } = request;
		assert.ok(Array.isArray(results) && results.length > 0, "the request lists results");
		const candidates = new Map<string, unknown>();
		for (const name of await names("candidate-")) {
			candidates.set(name, await fixture(name));
		}
		const slugs: string[] = [];
		const matched: string[] = [];
		for (const result of results) {
			assert.ok(isJsonObject(result) && typeof result.practiceSlug === "string");
			assert.deepEqual(Object.keys(result).toSorted(), ["candidate", "practiceSlug"]);
			slugs.push(result.practiceSlug);
			const parsed = accepted(result.candidate);
			assert.deepStrictEqual(parsed, result.candidate, result.practiceSlug);
			const same = [...candidates].filter(([, value]) => isDeepStrictEqual(parsed, value));
			assert.equal(same.length, 1, `${result.practiceSlug} is one candidate fixture`);
			matched.push(same[0]?.[0] ?? "");
		}
		assert.deepEqual(slugs, slugs.toSorted(), "sorted by slug");
		assert.equal(new Set(slugs).size, slugs.length, "one result per slug");
		assert.deepEqual(
			matched.toSorted(),
			[...candidates.keys()].toSorted(),
			"each candidate fixture once",
		);
	});

	void test("every invalid fixture is refused with exactly the code its sidecar names", async () => {
		const sidecar = await fixtureObject("invalid-refusals.json");
		const invalid = await names("invalid-");
		const files = invalid.filter((name) => name !== "invalid-refusals.json");
		assert.deepEqual(
			Object.keys(sidecar).toSorted(),
			files.toSorted(),
			"the sidecar names every invalid file",
		);
		assert.deepEqual(
			new Set(Object.values(sidecar)),
			new Set(CANDIDATE_REFUSALS),
			"the fixtures refuse with every code",
		);
		for (const name of files) {
			const code = sidecar[name];
			assert.ok(
				CANDIDATE_REFUSALS.some((refusal) => refusal === code),
				`${name}: ${String(code)} is a refusal code`,
			);
			assert.equal(refusalOf(await fixture(name)), code, name);
		}
	});

	void test("the inputs the server's live harnesses stage are what the host parses", async () => {
		// LiveWorkRecordsTest pins these from the harnesses, whose work records the content sources build.
		const files = await readdir(path.join(FIXTURES, "work-records"));
		const kinds = files.toSorted((a, b) => a.localeCompare(b));
		assert.deepEqual(kinds, ["scm.issue.json", "scm.pull_request.json"]);
		for (const name of kinds) {
			const staged = await fixtureObject(`work-records/${name}`);
			assert.ok(isJsonObject(staged.cause), name);
			const parsed = practiceInputsSchema.safeParse({
				...staged.cause,
				previous: null,
				work: staged.work,
			});
			assert.ok(parsed.success, `${name}: ${parsed.error?.message ?? ""}`);
			assert.equal(parsed.data.kind, name.replace(/\.json$/u, ""));
		}
	});

	void test("the execution report parses, with one entry per practice sorted by slug", async () => {
		const report = executionReportSchema.parse(await fixture("execution-report.json"));
		const slugs = report.practices.map((entry) => entry.practiceSlug);
		assert.equal(slugs.length, 7);
		assert.deepEqual(slugs, slugs.toSorted());
		assert.equal(new Set(slugs).size, slugs.length);
		const failed = report.practices.filter((entry) => entry.status === "failed");
		assert.deepEqual(
			failed.map(({ stopReason, error }) => ({ stopReason, error })),
			[{ stopReason: "invalid-program", error: LEGACY_CONTRACT_ERROR }],
		);
		// The practices whose program passed are the ones the admission request carries.
		const request = await fixtureObject("admission-request.json");
		const admitted = Array.isArray(request.results)
			? request.results.map((result) => (isJsonObject(result) ? result.practiceSlug : undefined))
			: [];
		assert.deepEqual(
			report.practices.filter((entry) => entry.status === "ok").map((entry) => entry.practiceSlug),
			admitted,
		);
	});
});

/** A complete MET result with host coverage. */
function met(): Record<string, unknown> {
	return {
		outcome: "MET",
		summary: "Added comments explain why",
		explanation: "Each added comment states a constraint that the code cannot express.",
		citations: [citation(10)],
		evidence: {
			search: {
				consulted: ["scm.pull-request.diff"],
				lookedFor: "comments",
				boundary: "the change",
			},
		},
		coverage: { source: "host", reads: 2, truncatedReads: 0, failedReads: 0, complete: true },
	};
}

function citation(line: number, quote = "  // why"): Record<string, unknown> {
	return {
		sourceKind: "scm.pull-request.diff",
		artifactPath: "context/change.json",
		path: "src/a.ts",
		side: "NEW",
		startLine: line,
		endLine: line,
		quote,
	};
}

function problem(line: number, fields: Record<string, unknown> = {}): Record<string, unknown> {
	return {
		severity: "MAJOR",
		summary: `A problem at line ${line}`,
		explanation: "The added line drops the error.",
		citations: [citation(line, "  } catch {}")],
		basis: { type: "introduced", head: 0, explanation: "The change adds the line." },
		...fields,
	};
}

/** A complete NOT_MET result with two problems, an example and claimed coverage. */
function notMet(): Record<string, unknown> {
	return {
		outcome: "NOT_MET",
		summary: "Two added catch blocks drop errors",
		explanation: "Two catch blocks end the failure path without a trace.",
		citations: [],
		problems: [problem(20), problem(30, { severity: "MINOR" })],
		example: { citation: citation(40, "  throw error;"), note: "This catch rethrows." },
		evidence: {},
		coverage: { source: "claimed", selected: 3, checked: 3, total: 3, complete: true },
	};
}

function notApplicable(): Record<string, unknown> {
	return {
		outcome: "NOT_APPLICABLE",
		summary: "No user interface changes",
		explanation: "The change touches no view.",
		citations: [citation(1, "export {};")],
		evidence: {
			inapplicability: {
				consulted: ["scm.pull-request.diff"],
				subject: "views",
				ruledOutBy: "no view file",
			},
		},
		coverage: { source: "claimed", selected: 1, checked: 1, total: 1, complete: true },
	};
}

function undetermined(): Record<string, unknown> {
	return {
		outcome: "UNDETERMINED",
		summary: "The header's origin is unclear",
		explanation: "Who sets the header decides it.",
		citations: [citation(5, "  read(header);")],
		evidence: {
			undecidability: [
				{ openQuestion: "Who sets the header?", wouldSettleIt: "The gateway configuration." },
			],
		},
		coverage: { source: "host", reads: 1, truncatedReads: 0, failedReads: 0, complete: true },
	};
}

const BASES = { met, notMet, notApplicable, undetermined };

/** One change to a valid result that breaks exactly one rule. */
type Breach = (value: Record<string, unknown>) => unknown;

const incomplete = {
	source: "host",
	reads: 2,
	truncatedReads: 1,
	failedReads: 0,
	complete: false,
	omitted: [{ what: "lines beyond the read limit", count: 1, reason: "truncated" }],
};

const problemsOf = (value: Record<string, unknown>): Record<string, unknown>[] =>
	Array.isArray(value.problems) ? value.problems.filter(isJsonObject) : [];
const evidenceOf = (value: Record<string, unknown>): Record<string, unknown> =>
	isJsonObject(value.evidence) ? value.evidence : {};

/** For each refusal code, a base result and the one change that breaks only that rule. */
const BREACHES: Record<CandidateRefusal, [keyof typeof BASES, Breach]> = {
	malformed_candidate: ["notMet", (v) => Object.assign(v, { extra: true })],
	outcome_problem_mismatch: ["met", (v) => Object.assign(v, { problems: [problem(20)] })],
	severity_outcome_mismatch: ["met", (v) => Object.assign(v, { severity: "MINOR" })],
	severity_below_worst_problem: ["notMet", (v) => Object.assign(v, { severity: "MINOR" })],
	warrant_outcome_mismatch: [
		"notMet",
		(v) =>
			Object.assign(evidenceOf(v), {
				inapplicability: { consulted: ["scm.pull-request.diff"], subject: "s", ruledOutBy: "r" },
			}),
	],
	result_citations_required: ["met", (v) => Object.assign(v, { citations: [] })],
	met_coverage_incomplete: ["met", (v) => Object.assign(v, { coverage: incomplete })],
	coverage_inconsistent: [
		"notMet",
		(v) =>
			Object.assign(v, {
				coverage: {
					source: "claimed",
					selected: 3,
					checked: 4,
					total: 3,
					complete: false,
					omitted: incomplete.omitted,
				},
			}),
	],
	example_outcome_mismatch: [
		"undetermined",
		(v) =>
			Object.assign(v, {
				example: { citation: citation(40, "  throw error;"), note: "A good catch." },
			}),
	],
	example_overlaps_problem: [
		"notMet",
		(v) =>
			Object.assign(v, { example: { citation: citation(30, "  } catch {}"), note: "Overlaps." } }),
	],
	basis_citation_out_of_range: [
		"notMet",
		(v) => {
			const [first] = problemsOf(v);
			Object.assign(first ?? {}, {
				basis: { type: "worsened", base: 0, head: 1, explanation: "e" },
			});
		},
	],
	duplicate_problem_key: [
		"notMet",
		(v) => {
			for (const each of problemsOf(v)) {
				Object.assign(each, { key: "same-key" });
			}
		},
	],
};

function broken(base: keyof typeof BASES, ...codes: CandidateRefusal[]): Record<string, unknown> {
	const value = BASES[base]();
	for (const code of codes) {
		BREACHES[code][1](value);
	}
	return value;
}

void describe("the candidate rules", () => {
	void test("a record's display path cannot separate an example from a problem on its artifact", () => {
		const record = {
			sourceKind: "scm.pull-request.comments",
			artifactPath: "context/comments.json",
			path: "comment label",
			startLine: 1,
			endLine: 2,
			quote: "A comment",
		};
		const value = {
			...notMet(),
			problems: [problem(1, { citations: [record] })],
			example: {
				citation: { ...record, path: "another label", startLine: 2 },
				note: "The same comment.",
			},
		};
		assert.equal(refusalOf(value), "example_overlaps_problem");
		assert.equal(
			refusalOf({
				...value,
				example: {
					citation: { ...record, artifactPath: "context/other-comments.json" },
					note: "Another artifact.",
				},
			}),
			"accepted",
		);
	});
	void test("each base result is valid", () => {
		for (const make of Object.values(BASES)) {
			accepted(make());
		}
	});

	void test("every refusal code is reachable by breaking its rule alone", () => {
		for (const code of CANDIDATE_REFUSALS) {
			const [base] = BREACHES[code];
			assert.equal(refusalOf(broken(base, code)), code, code);
		}
	});

	void test("a result that breaks two rules is refused with the code of the earlier rule", () => {
		const pairs: [keyof typeof BASES, CandidateRefusal, CandidateRefusal][] = [
			["notMet", "malformed_candidate", "severity_below_worst_problem"],
			["met", "malformed_candidate", "outcome_problem_mismatch"],
			["met", "outcome_problem_mismatch", "severity_outcome_mismatch"],
			["met", "severity_outcome_mismatch", "result_citations_required"],
			["notMet", "severity_below_worst_problem", "warrant_outcome_mismatch"],
			["notMet", "warrant_outcome_mismatch", "coverage_inconsistent"],
			["met", "result_citations_required", "met_coverage_incomplete"],
			["notMet", "coverage_inconsistent", "example_overlaps_problem"],
			["notMet", "example_overlaps_problem", "basis_citation_out_of_range"],
			["notMet", "basis_citation_out_of_range", "duplicate_problem_key"],
			["undetermined", "example_outcome_mismatch", "malformed_candidate"],
		];
		for (const [base, first, second] of pairs) {
			const expected =
				CANDIDATE_REFUSALS.indexOf(first) < CANDIDATE_REFUSALS.indexOf(second) ? first : second;
			assert.equal(refusalOf(broken(base, first, second)), expected, `${first} with ${second}`);
			assert.equal(refusalOf(broken(base, second, first)), expected, `${second} with ${first}`);
		}
	});

	void test("a MET result with its problems is named by the outcome rule, never as an unknown key", () => {
		assert.equal(refusalOf(broken("met", "outcome_problem_mismatch")), "outcome_problem_mismatch");
	});

	void test("an UNDETERMINED result without an open question breaks the warrant rule before the citation rule", () => {
		const value = undetermined();
		value.evidence = {};
		value.citations = [];
		assert.equal(refusalOf(value), "warrant_outcome_mismatch");
	});

	void test("a NOT_MET result needs no result citation, and any other outcome needs one", () => {
		accepted(notMet());
		for (const make of [met, notApplicable, undetermined]) {
			const value = make();
			value.citations = [];
			assert.equal(refusalOf(value), "result_citations_required", String(value.outcome));
		}
	});

	void test("host coverage is complete exactly when no read was cut short or failed", () => {
		const value = undetermined();
		value.coverage = {
			source: "host",
			reads: 1,
			truncatedReads: 0,
			failedReads: 1,
			complete: true,
		};
		assert.equal(refusalOf(value), "coverage_inconsistent");
		value.coverage = { ...incomplete, truncatedReads: 0 };
		assert.equal(refusalOf(value), "coverage_inconsistent");
		value.coverage = incomplete;
		accepted(value);
	});

	void test("a leads return is malformed: nothing guesses what it meant", () => {
		assert.equal(refusalOf({ leads: [], facts: {} }), "malformed_candidate");
		assert.equal(refusalOf(null), "malformed_candidate");
		assert.equal(refusalOf("MET"), "malformed_candidate");
	});
});

void describe("the candidate bounds", () => {
	void test("a summary is one line of at most 160 characters", () => {
		const value = met();
		value.summary = "s".repeat(CANDIDATE_LIMITS.summaryChars);
		accepted(value);
		value.summary = "s".repeat(CANDIDATE_LIMITS.summaryChars + 1);
		assert.equal(refusalOf(value), "malformed_candidate");
		value.summary = "first line\nsecond line";
		assert.equal(refusalOf(value), "malformed_candidate");
		assert.equal(CANDIDATE_LIMITS.summaryChars, 160);
	});

	void test("a quote holds at most 4000 characters", () => {
		const value = met();
		value.citations = [citation(1, "q".repeat(CANDIDATE_LIMITS.quoteChars))];
		accepted(value);
		value.citations = [citation(1, "q".repeat(CANDIDATE_LIMITS.quoteChars + 1))];
		assert.equal(refusalOf(value), "malformed_candidate");
		assert.equal(CANDIDATE_LIMITS.quoteChars, 4000);
	});

	void test("a candidate holds at most 131072 bytes", () => {
		assert.equal(CANDIDATE_LIMITS.candidateBytes, 131_072);
		const value = met();
		const quotes = Array.from({ length: 32 }, (_, i) =>
			citation(i + 1, "q".repeat(CANDIDATE_LIMITS.quoteChars)),
		);
		value.citations = quotes;
		const over = Buffer.byteLength(JSON.stringify(value)) - CANDIDATE_LIMITS.candidateBytes;
		assert.ok(over > 0 && over < CANDIDATE_LIMITS.quoteChars, `${over} bytes over`);
		// Cut the last quote so that the candidate holds exactly the bound.
		quotes[quotes.length - 1] = citation(32, "q".repeat(CANDIDATE_LIMITS.quoteChars - over));
		assert.equal(Buffer.byteLength(JSON.stringify(value)), CANDIDATE_LIMITS.candidateBytes);
		accepted(value);
		quotes[quotes.length - 1] = citation(32, "q".repeat(CANDIDATE_LIMITS.quoteChars - over + 1));
		const parsed = parseCandidate(value);
		assert.ok(!parsed.ok);
		assert.equal(parsed.reasonCode, "malformed_candidate");
		assert.match(parsed.message, /131073 bytes; at most 131072/u);
	});

	void test("a count is an integer from 0 to 1 000 000", () => {
		const value = undetermined();
		value.coverage = {
			source: "host",
			reads: CANDIDATE_LIMITS.maxCount,
			truncatedReads: 0,
			failedReads: 0,
			complete: true,
		};
		accepted(value);
		for (const reads of [CANDIDATE_LIMITS.maxCount + 1, -1, 1.5]) {
			value.coverage = { source: "host", reads, truncatedReads: 0, failedReads: 0, complete: true };
			assert.equal(refusalOf(value), "malformed_candidate", String(reads));
		}
	});
});

void describe("the program's meta", () => {
	void test("names the direct models a program needs, and nothing else", () => {
		assert.deepEqual(parseMeta({}), { meta: {} });
		assert.deepEqual(parseMeta({ models: { chat: "required", embedding: "optional" } }), {
			meta: { models: { chat: "required", embedding: "optional" } },
		});
		for (const meta of [
			{ models: { chat: "sometimes" } },
			{ models: { vision: "required" } },
			{ limits: {} },
			null,
			"meta",
		]) {
			const parsed = parseMeta(meta);
			assert.ok("error" in parsed, JSON.stringify(meta));
			assert.equal(parsed.legacy, false);
			assert.match(parsed.error, /^meta: /u);
		}
	});

	void test("a key of the retired leads contract is refused with the legacy error that names it", () => {
		assert.equal(legacyContractError("version"), LEGACY_CONTRACT_ERROR);
		for (const key of ["version", "kinds", "tokens"] as const) {
			assert.deepEqual(
				parseMeta({ models: { chat: "required" }, [key]: key === "kinds" ? {} : 1 }),
				{
					error: legacyContractError(key),
					legacy: true,
				},
			);
			assert.match(legacyContractError(key), new RegExp(`\\(meta\\.${key}\\)`, "u"));
		}
		// The first retired key in the contract's order is named.
		assert.deepEqual(parseMeta({ tokens: 1, kinds: {} }), {
			error: legacyContractError("kinds"),
			legacy: true,
		});
	});
});

/** Review options with one prepared context value, of exactly `bytes` bytes as JSON. */
function sized(bytes: number) {
	const overhead = Buffer.byteLength(JSON.stringify({ context: [{ label: "Data", value: "" }] }));
	return { context: [{ label: "Data", value: "v".repeat(bytes - overhead) }] };
}

const lead = (n: number, label = "A line to check.") => ({
	at: { change: "src/a.ts", line: n + 1 },
	label,
});
const omission = { what: "added comments", count: 1, reason: "unavailable" };

void describe("the review options", () => {
	void test("no options are an empty input", () => {
		assert.deepEqual(parseReviewOptions(undefined), { options: {} });
		assert.deepEqual(parseReviewOptions({}), { options: {} });
	});

	const bounds: [string, (n: number) => Record<string, unknown>, number][] = [
		["leads", (n) => ({ leads: Array.from({ length: n }, (_, i) => lead(i)) }), 200],
		[
			"directions",
			(n) => ({ directions: Array.from({ length: n }, (_, i) => `Direction ${i}.`) }),
			10,
		],
		[
			"context",
			(n) => ({
				context: Array.from({ length: n }, (_, i) => ({ label: `Value ${i}`, value: i })),
			}),
			20,
		],
		["limitations", (n) => ({ limitations: Array.from({ length: n }, () => omission) }), 20],
		[
			"facts",
			(n) => ({ facts: Object.fromEntries(Array.from({ length: n }, (_, i) => [`fact${i}`, i])) }),
			32,
		],
	];
	for (const [name, make, max] of bounds) {
		void test(`at most ${max} ${name}`, () => {
			assert.ok("options" in parseReviewOptions(make(max)));
			const parsed = parseReviewOptions(make(max + 1));
			assert.ok("error" in parsed);
			assert.match(parsed.error, new RegExp(`^review options: ${name}`, "u"));
		});
	}

	void test("a lead label and a direction are one line of at most 300 characters", () => {
		assert.ok(
			"options" in
				parseReviewOptions({ leads: [lead(0, "l".repeat(300))], directions: ["d".repeat(300)] }),
		);
		for (const options of [
			{ leads: [lead(0, "l".repeat(301))] },
			{ leads: [lead(0, "first\nsecond")] },
			{ directions: ["d".repeat(301)] },
			{ directions: ["first\nsecond"] },
		]) {
			assert.ok("error" in parseReviewOptions(options), JSON.stringify(options).slice(0, 80));
		}
	});

	void test("an invalid lead or an unknown key is an error, never dropped", () => {
		for (const options of [
			{ leads: [{ at: { change: "src/a.ts", line: 0 }, label: "l" }] },
			{ leads: [{ at: { change: "src/a.ts", line: 1 }, label: "l", kind: "retired" }] },
			{ leads: [{ at: { change: "src/a.ts", file: "src/a.ts", line: 1 }, label: "l" }] },
			{ unrated: [omission] },
		]) {
			assert.ok("error" in parseReviewOptions(options), JSON.stringify(options));
		}
	});

	void test(`the options hold at most ${MAX_REVIEW_OPTIONS_BYTES} bytes`, () => {
		assert.ok("options" in parseReviewOptions(sized(MAX_REVIEW_OPTIONS_BYTES)));
		const parsed = parseReviewOptions(sized(MAX_REVIEW_OPTIONS_BYTES + 1));
		assert.ok("error" in parsed);
		assert.equal(
			parsed.error,
			`review options have ${MAX_REVIEW_OPTIONS_BYTES + 1} bytes; at most ${MAX_REVIEW_OPTIONS_BYTES}`,
		);
	});
});
