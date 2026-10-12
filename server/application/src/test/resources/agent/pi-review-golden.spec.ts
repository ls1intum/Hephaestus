import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import {
	mkdirSync,
	mkdtempSync,
	readdirSync,
	readFileSync,
	rmSync,
	symlinkSync,
	writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import nodePath from "node:path";
import { mock, test } from "node:test";

import {
	GOLDEN_RECORD,
	GOLDEN_DIRECTIONS,
	GOLDEN_FACTS,
	GOLDEN_UNRATED,
	LEAD_LABELS,
	PRACTICES_DIR,
	goldenLeads,
	stageGoldenWorkspace,
} from "./golden-review-workspace.ts";
import { REVIEWED_CHANGES } from "./golden-reviewed-changes.ts";

// Golden prompts: what one practice's review session is told. The runner reads /workspace at module scope, so each
// scenario is a child process that re-enters this file with the SDK mocked. The frozen prompts in golden-prompts/
// were captured from commit 5347f0108 over the same workspace (golden-review-workspace.ts); only the reviewed blocks
// of golden-reviewed-changes.ts may differ. golden-prompts/final/ pins the new submit_result tool and the new form of
// the prepared review input.

const GOLDEN = nodePath.join(import.meta.dirname, "golden-prompts");
const LIB = nodePath.resolve(
	import.meta.dirname,
	"../../../../../../docker/agents/practice-scripts/lib",
);

// The workspace captures no checkout witness, so a quote of code would be refused; the record is a staged source.
const recordCitation = {
	sourceKind: "scm.pull-request.core",
	artifactPath: "evidence/metadata.json",
	path: "evidence/metadata.json",
	startLine: 1,
	endLine: 1,
	quote: JSON.stringify(GOLDEN_RECORD),
};

/** The program that prepares the same input as the retired leads script, and asks one review with it. */
function preparingProgram(many: boolean): string {
	const leads = goldenLeads(many).map(({ kind, ...lead }) => ({
		...lead,
		label: LEAD_LABELS[kind],
	}));
	return `import { definePracticeScript } from "../lib/practice-script.ts";
export default definePracticeScript({
meta: {},
async run(ctx) {
	return ctx.review(${JSON.stringify({ leads, facts: GOLDEN_FACTS, directions: GOLDEN_DIRECTIONS, limitations: GOLDEN_UNRATED })});
},
});
`;
}

/** Stage the workspace, run the runner over it, and return what its review session was told. */
function runScenario(name: string): Map<string, string> {
	const cwd = mkdtempSync(nodePath.join(tmpdir(), "pi-golden-"));
	try {
		stageGoldenWorkspace(
			cwd,
			5,
			readFileSync(
				new URL("../../../main/resources/agent/pi-orchestrator.md", import.meta.url),
				"utf8",
			),
		);
		if (name.startsWith("leads")) {
			writeFileSync(nodePath.join(cwd, "package.json"), '{"type":"module"}\n');
			symlinkSync(LIB, nodePath.join(cwd, nodePath.dirname(PRACTICES_DIR), "lib"));
			writeFileSync(
				nodePath.join(cwd, PRACTICES_DIR, "test-practice.ts"),
				preparingProgram(name === "leads-truncated"),
			);
		}
		const child = spawnSync(
			process.execPath,
			["--experimental-test-module-mocks", import.meta.filename],
			{
				env: {
					...process.env,
					PI_GOLDEN_SCENARIO: name.startsWith("leads") ? "leads" : name,
					PI_RUNNER_CWD: cwd,
					PI_CODING_AGENT_DIR: cwd,
					AGENT_BUDGET_MS: "60000",
					PI_PRACTICE_MODEL_CALLS: "2",
					PI_PRACTICE_OUTPUT_TOKENS: "8000",
					PI_DIRECT_MODEL_TOKENS: "1000",
					LLM_PROXY_URL: "https://unused.invalid",
					LLM_PROXY_TOKEN: "test-token",
				},
				encoding: "utf8",
				timeout: 30_000,
			},
		);
		assert.equal(child.error, undefined);
		assert.equal(child.status, 0, child.stderr);
		const captured = nodePath.join(cwd, "captured");
		return new Map(
			readdirSync(captured).map((file) => [
				file,
				readFileSync(nodePath.join(captured, file), "utf8"),
			]),
		);
	} finally {
		rmSync(cwd, { recursive: true, force: true });
	}
}

/** The prepared review input's section of a turn, and the turn without it. */
function cutSection(prompt: string): { section: string; rest: string } {
	const start = prompt.indexOf("#### Leads from the practice script");
	if (start === -1) {
		return { section: "", rest: prompt };
	}
	const end = prompt.search(/\n\n(?:Record one observation|Submit one result) for /u);
	assert.ok(end > start, "the section ends before the closing instruction");
	return { section: prompt.slice(start, end), rest: prompt.slice(0, start) + prompt.slice(end) };
}

/** The headings of a section and the part of it each one starts. */
const SECTION_PARTS = new Map<string, "kinds" | "leads" | "facts">([
	["**Lead kinds:**", "kinds"],
	["**Leads:**", "leads"],
	["**Facts:**", "facts"],
]);

/** What a section says, line kind by line kind: directions, leads in order, facts, not-rated notices, cut notice. */
function sectionContent(section: string, legacy: boolean) {
	const lines = section.split("\n").slice(1);
	const kinds = new Map<string, string>();
	const leads: string[] = [];
	const directions: string[] = [];
	const facts: string[] = [];
	const notRated: string[] = [];
	let cut: string | null = null;
	let part: "directions" | "kinds" | "leads" | "facts" = "directions";
	let label = "";
	for (const line of lines) {
		const heading = SECTION_PARTS.get(line);
		if (heading !== undefined) {
			part = heading;
			continue;
		}
		if (line === "") {
			continue;
		}
		if (line.startsWith("- Not rated: ")) {
			notRated.push(line);
		} else if (/^- \d+ more lines are not shown/u.test(line)) {
			cut = line;
		} else if (part === "directions") {
			directions.push(line);
		} else if (part === "kinds") {
			const kind = /^- (?<kind>[a-z-]+): (?<label>.*)$/u.exec(line)?.groups;
			assert.ok(kind?.kind !== undefined && kind.label !== undefined, line);
			kinds.set(kind.kind, kind.label);
		} else if (part === "facts") {
			facts.push(line);
		} else if (legacy) {
			const row = /^- (?<kind>[a-z-]+) at (?<rest>.*)$/u.exec(line)?.groups;
			assert.ok(row?.kind !== undefined && row.rest !== undefined, line);
			leads.push(`${kinds.get(row.kind) ?? row.kind} at ${row.rest}`);
		} else if (line.startsWith("  - at ")) {
			leads.push(`${label} at ${line.slice("  - at ".length)}`);
		} else {
			label = line.slice("- ".length);
		}
	}
	return { directions, leads, facts, notRated, cut };
}

/** The tool names a captured tool list holds. */
function toolsOf(text: string): unknown {
	const parsed: unknown = JSON.parse(text);
	return typeof parsed === "object" && parsed !== null ? Reflect.get(parsed, "tools") : null;
}

const scenario = process.env.PI_GOLDEN_SCENARIO;
if (scenario !== undefined && scenario !== "") {
	const cwd = process.env.PI_RUNNER_CWD;
	assert.ok(cwd !== undefined && cwd !== "");
	const capture = (name: string, text: string) =>
		writeFileSync(nodePath.join(cwd, "captured", name), text);
	mkdirSync(nodePath.join(cwd, "captured"), { recursive: true });
	// The workspace stages the change view the runner would derive from a checkout it does not have.
	const change = new URL("../../../main/resources/agent/pi-change.ts", import.meta.url).href;
	const derived: unknown = await import(change);
	assert.ok(typeof derived === "object" && derived !== null);
	mock.module(change, { namedExports: { ...derived, writeChangeView: () => undefined } });
	mock.method(globalThis, "fetch", async (_input: unknown, init?: RequestInit) => {
		assert.ok(typeof init?.body === "string");
		const posted: unknown = JSON.parse(init.body);
		assert.ok(typeof posted === "object" && posted !== null);
		const results: unknown = Reflect.get(posted, "results");
		assert.ok(Array.isArray(results));
		return Response.json({
			schemaVersion: 2,
			admissionDigest: "golden-digest",
			results: [],
			withheld: [],
		});
	});
	const manager = {
		appendCustomEntry: () => undefined,
		getSessionFile: () => undefined,
		getSessionId: () => "golden-session",
		buildSessionProjection: () => ({ messages: [] }),
	};
	let prompts = 0;
	let steers = 0;
	interface CustomTool {
		name: string;
		description: string;
		parameters: unknown;
		prepareArguments?: (args: unknown) => unknown;
		execute: (id: string, input: unknown) => Promise<unknown>;
	}
	mock.module("@earendil-works/pi-coding-agent", {
		namedExports: {
			defineTool: (tool: unknown) => tool,
			estimateTokens: (message: { content: string }) => Math.ceil(message.content.length / 4),
			createCodemodeExtension: () => () => undefined,
			getAgentDir: () => cwd,
			DefaultResourceLoader: class {
				constructor(options: { systemPrompt: string }) {
					capture("system-prompt.md", options.systemPrompt);
				}
				readonly loaded = Promise.resolve();
				async reload() {
					await this.loaded;
				}
			},
			SettingsManager: { create: () => ({}) },
			SessionManager: { create: () => manager, inMemory: () => manager, open: () => manager },
			ModelRuntime: {
				async create() {
					return {
						registerProvider: () => undefined,
						getModel: () => ({ id: "test-model", contextWindow: 128_000, maxTokens: 16_384 }),
					};
				},
			},
			async createAgentSession(options: { tools: string[]; customTools: CustomTool[] }) {
				const handlers = new Set<(event: unknown) => void>();
				const emit = (event: unknown) => {
					for (const handler of handlers) {
						handler(event);
					}
				};
				capture(
					"tools.json",
					`${JSON.stringify(
						{
							tools: options.tools,
							customTools: options.customTools.map((custom) => ({
								name: custom.name,
								description: custom.description,
								parameters: custom.parameters,
							})),
						},
						null,
						1,
					)}\n`,
				);
				const tool = (name: string) => {
					const found = options.customTools.find((item) => item.name === name);
					assert.ok(found, `${name} is registered`);
					return async (id: string, args: unknown) =>
						found.execute(id, found.prepareArguments?.(args) ?? args);
				};
				const submitResult = async () => {
					const reply = await tool("submit_result")("golden-1", {
						outcome: "NOT_MET",
						summary: "Added authentication call is insecure",
						explanation: "The changed authentication code calls insecure().",
						citations: [],
						problems: [
							{
								severity: "MAJOR",
								summary: "Login calls insecure() on every sign-in",
								explanation:
									"The added line calls insecure() where the criteria ask for checked().",
								citations: [recordCitation],
								basis: {
									type: "duty",
									criterionQuote: "Added authentication calls use checked(), not insecure().",
									event: 0,
									explanation:
										"The pull request adds a sign-in, which the criteria require to use checked().",
								},
							},
						],
						evidence: {},
					});
					capture("submitted.json", `${JSON.stringify(reply, null, 1)}\n`);
				};
				const callUsage = {
					input: 1000,
					output: 100,
					cacheRead: 0,
					cacheWrite: 0,
					cost: { total: 0 },
				};
				return {
					extensionsResult: { errors: [] },
					session: {
						state: { messages: [] },
						agent: { state: { tools: [] }, finishTurn: async () => ({ action: "continue" }) },
						systemPrompt: "",
						model: { contextWindow: 128_000, maxTokens: 16_384 },
						settingsManager: { getCompactionSettings: () => ({ reserveTokens: 16_384 }) },
						sessionManager: manager,
						subscribe(handler: (event: unknown) => void) {
							handlers.add(handler);
							return () => {
								handlers.delete(handler);
							};
						},
						clearQueue: () => undefined,
						pendingMessageCount: 0,
						getContextUsage: () => ({ tokens: 1000, contextWindow: 128_000, percent: 0 }),
						compact: async () => ({}),
						isIdle: true,
						isStreaming: false,
						waitForIdle: async () => undefined,
						abortCompaction: () => undefined,
						abort: async () => undefined,
						dispose: () => undefined,
						steer: async (message?: string) => {
							steers += 1;
							capture(`steer-${steers}.md`, message ?? "");
						},
						async prompt(
							text: string,
							promptOptions?: { preflightResult?: (disposition: string) => void },
						) {
							promptOptions?.preflightResult?.("started");
							prompts += 1;
							capture(`prompt-${prompts}.md`, text);
							if (prompts === 1 && scenario === "nudge") {
								// The model reads until its turn's remaining work only pays for submitting.
								for (let call = 1; call <= 4; call += 1) {
									emit({
										type: "message_end",
										message: {
											role: "assistant",
											stopReason: "toolUse",
											usage: callUsage,
											content: [{ type: "toolCall", id: `r-${call}`, name: "read", arguments: {} }],
										},
									});
									emit({ type: "turn_end" });
								}
								return;
							}
							if (prompts === 1 && scenario === "compaction") {
								emit({
									type: "compaction_end",
									reason: "threshold",
									result: undefined,
									aborted: false,
								});
								return;
							}
							await submitResult();
						},
					},
				};
			},
		},
	});
	await import("../../../main/resources/agent/pi-runner.ts");
} else {
	const SCENARIOS = ["first-turn", "nudge", "compaction", "leads", "leads-truncated"];
	const captures = new Map(SCENARIOS.map((name) => [name, runScenario(name)]));
	if (process.env.PI_GOLDEN_DUMP !== undefined) {
		for (const [name, captured] of captures) {
			const dir = nodePath.join(process.env.PI_GOLDEN_DUMP, name);
			mkdirSync(dir, { recursive: true });
			for (const [file, text] of captured) {
				writeFileSync(nodePath.join(dir, file), text);
			}
		}
	}

	for (const name of SCENARIOS) {
		void test(`only the reviewed output-contract blocks of the ${name} prompts changed since 5347f0108`, () => {
			const captured = captures.get(name);
			assert.ok(captured !== undefined);
			const frozen = nodePath.join(GOLDEN, name);
			for (const file of readdirSync(frozen)) {
				if (file === "tools.json") {
					continue;
				}
				let expected = readFileSync(nodePath.join(frozen, file), "utf8");
				let actual = captured.get(file);
				assert.ok(actual !== undefined, `${name}/${file} was not told`);
				if (name.startsWith("leads")) {
					// The prepared input: the same labels, directions, leads in order with their quotes and facts, notices
					// and cut, as far as both sections show them; its new form is pinned whole beside the frozen turn.
					const before = cutSection(expected);
					const after = cutSection(actual);
					if (before.section !== "") {
						const old = sectionContent(before.section, true);
						const now = sectionContent(after.section, false);
						assert.deepEqual(now.directions, old.directions);
						assert.deepEqual(now.notRated, old.notRated);
						const shown = Math.min(old.leads.length, now.leads.length);
						assert.ok(shown > 0);
						assert.deepEqual(now.leads.slice(0, shown), old.leads.slice(0, shown));
						if (old.facts.length > 0 && now.facts.length > 0) {
							assert.deepEqual(now.facts, old.facts);
						}
						assert.equal(now.cut !== null, old.cut !== null, "a cut section says so, now as then");
						assert.equal(
							after.section,
							readFileSync(nodePath.join(GOLDEN, "final", `${name}-review-input.md`), "utf8"),
						);
						expected = before.rest;
						actual = after.rest;
					}
				}
				for (const [block, replacement] of REVIEWED_CHANGES[
					file === "system-prompt.md" ? "system" : "turn"
				]) {
					expected = expected.split(block).join(replacement);
				}
				assert.equal(actual, expected, `${name}/${file}`);
			}
		});
	}

	void test("the review session's tools are the evidence tools and submit_result, as the final contract fixes them", () => {
		const final = readFileSync(nodePath.join(GOLDEN, "final", "tools.json"), "utf8");
		for (const name of SCENARIOS) {
			assert.equal(captures.get(name)?.get("tools.json"), final, name);
		}
		// The frozen session had the same evidence tools, with report_observation where submit_result is now.
		const frozen = toolsOf(readFileSync(nodePath.join(GOLDEN, "first-turn/tools.json"), "utf8"));
		assert.ok(Array.isArray(frozen));
		assert.deepEqual(
			toolsOf(final),
			frozen.map((tool: unknown) => (tool === "report_observation" ? "submit_result" : tool)),
		);
	});

	void test("the built-in program opens one native review session and returns its result, with no measuring session after it", () => {
		const captured = captures.get("first-turn");
		assert.ok(captured !== undefined);
		assert.deepEqual(
			[...captured.keys()].filter((file) => file.startsWith("prompt-")),
			["prompt-1.md"],
		);
		assert.match(captured.get("submitted.json") ?? "", /test-practice: stored \(negative\)/u);
	});
}
