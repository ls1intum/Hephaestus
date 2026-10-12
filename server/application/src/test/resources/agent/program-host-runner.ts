/**
 * Runs the real runner over a staged pull request review with several admitted practices, the Pi SDK mocked, the
 * change view staged, and the admission answered. The runner reads /workspace at module scope, so each run is a
 * child process that enters this file with `PI_HOST_SCENARIO` set. What the mocks saw goes to `captured/`. A mocked
 * session submits {@link SESSION_CANDIDATE}, or the JSON in `PI_HOST_SESSION_CANDIDATE` when that is set.
 */
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import {
	appendFileSync,
	existsSync,
	mkdirSync,
	readFileSync,
	symlinkSync,
	writeFileSync,
} from "node:fs";
import nodePath from "node:path";
import { mock } from "node:test";

import {
	GOLDEN_CAUSE,
	GOLDEN_RECORD,
	PRACTICES_DIR,
	stageGoldenWorkspace,
} from "./golden-review-workspace.ts";

const LIB = nodePath.resolve(
	import.meta.dirname,
	"../../../../../../docker/agents/practice-scripts/lib",
);

/** What the mocked review session of a practice does: submit a result, end without one, or never end. */
export type SessionBehaviour = "submit" | "silent" | "hang";

/** One admitted practice: its custom program (none for the built-in), and whether its inputs are staged. */
export interface StagedPractice {
	slug: string;
	program?: string;
	inputs?: boolean;
}

/**
 * The result the mocked session submits: a negative result on the pull request record. The workspace captures no
 * checkout witness, so a quote of code would be refused before admission.
 */
export const SESSION_CANDIDATE = {
	outcome: "NOT_MET",
	summary: "Added authentication call is insecure",
	explanation: "The changed authentication code calls insecure().",
	citations: [],
	problems: [
		{
			severity: "MAJOR",
			summary: "Login calls insecure() on every sign-in",
			explanation: "The added line calls insecure() where the criteria ask for checked().",
			citations: [
				{
					sourceKind: "scm.pull-request.core",
					artifactPath: "evidence/metadata.json",
					path: "evidence/metadata.json",
					startLine: 1,
					endLine: 1,
					quote: JSON.stringify(GOLDEN_RECORD),
				},
			],
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
};

/**
 * The source of a custom program that returns a code-only result with no review: the work's record cited, and
 * claimed coverage. `before` runs first in `run(ctx)`, with `generateText` and `step` in scope, and may set
 * `note`, which the summary carries.
 */
export function directProgram(before = ""): string {
	return `import { definePracticeScript, generateText, step } from "../lib/practice-script.ts";
export default definePracticeScript({
	meta: { models: { chat: "optional" } },
	async run(ctx) {
		let note = "none";
		${before}
		return {
			outcome: "NOT_APPLICABLE",
			summary: \`No user interface changes; note \${note}\`,
			explanation: "The change adds no view, template or component.",
			citations: [await ctx.evidence.citeRecord("metadata.json", 1)],
			evidence: {
				inapplicability: {
					consulted: ["scm.pull-request.core"],
					subject: "user interface views",
					ruledOutBy: "The record names one changed Java file.",
				},
			},
			coverage: { source: "claimed", selected: 1, checked: 1, total: 1, complete: true },
		};
	},
});
`;
}

/** Stage the review under `cwd`: the golden pull request, with the given practices admitted in one catalog group. */
export function stagePractices(cwd: string, practices: readonly StagedPractice[]): void {
	stageGoldenWorkspace(
		cwd,
		5,
		readFileSync(
			new URL("../../../main/resources/agent/pi-orchestrator.md", import.meta.url),
			"utf8",
		),
	);
	const write = (file: string, text: string) => {
		mkdirSync(nodePath.dirname(nodePath.join(cwd, file)), { recursive: true });
		writeFileSync(nodePath.join(cwd, file), text);
	};
	write("package.json", '{"type":"module"}\n');
	symlinkSync(LIB, nodePath.join(cwd, nodePath.dirname(PRACTICES_DIR), "lib"));
	write(
		"catalog/practices/index.json",
		JSON.stringify(
			practices.map(({ slug }) => ({
				slug,
				group: "security",
				readsSources: ["scm.pull-request.core", "scm.pull-request.diff"],
				exhaustiveSources: ["scm.pull-request.diff"],
				revisionId: 7,
			})),
		),
	);
	for (const { slug, program, inputs = true } of practices) {
		write(
			`catalog/practices/${slug}.md`,
			"# Uses secure authentication\n\n## Standard\nAdded authentication calls use checked(), not insecure().\n",
		);
		if (inputs) {
			write(`${PRACTICES_DIR}/${slug}.cause.json`, JSON.stringify(GOLDEN_CAUSE));
			write(`${PRACTICES_DIR}/${slug}.previous.json`, "null");
		}
		if (program !== undefined) {
			write(`${PRACTICES_DIR}/${slug}.ts`, program);
		}
	}
}

/** How one runner process ended, and what it wrote to stderr. */
export interface RunnerExit {
	status: number | null;
	stderr: string;
}

/**
 * Run the runner over `cwd` in a child process. The test process stays free to answer requests while it runs,
 * so a proxy stand-in in it can serve the runner's direct model calls.
 */
export async function runRunner(
	cwd: string,
	sessions: Record<string, SessionBehaviour>,
	env: Record<string, string> = {},
): Promise<RunnerExit> {
	const child = spawn(
		process.execPath,
		["--experimental-test-module-mocks", import.meta.filename],
		{
			env: {
				...process.env,
				PI_HOST_SCENARIO: JSON.stringify(sessions),
				PI_RUNNER_CWD: cwd,
				PI_CODING_AGENT_DIR: cwd,
				AGENT_BUDGET_MS: "60000",
				PI_PRACTICE_MODEL_CALLS: "12",
				PI_PRACTICE_OUTPUT_TOKENS: "16000",
				PI_DIRECT_MODEL_TOKENS: "200000",
				LLM_PROXY_URL: "https://unused.invalid",
				LLM_PROXY_TOKEN: "job-token",
				...env,
			},
			stdio: ["ignore", "ignore", "pipe"],
		},
	);
	let stderr = "";
	child.stderr.setEncoding("utf8");
	child.stderr.on("data", (chunk: string) => {
		stderr += chunk;
	});
	const timer = setTimeout(() => {
		child.kill("SIGKILL");
	}, 60_000);
	await once(child, "close");
	clearTimeout(timer);
	return { status: child.exitCode, stderr };
}

const parseJson = (text: string): unknown => JSON.parse(text);
const hasText = (value: string | undefined): value is string => value !== undefined && value !== "";

/** A JSON file the runner or a mock wrote under `cwd`. */
export function readJsonFile(cwd: string, file: string): unknown {
	return parseJson(readFileSync(nodePath.join(cwd, file), "utf8"));
}

/** A field of a parsed JSON value; undefined when the value is not an object. */
export function field(value: unknown, key: string): unknown {
	if (typeof value !== "object" || value === null) {
		return undefined;
	}
	const found: unknown = Reflect.get(value, key);
	return found;
}

/** The lines the mocks appended to `captured/<name>.jsonl`, parsed. */
export function capturedLines(cwd: string, name: string): unknown[] {
	const file = nodePath.join(cwd, "captured", `${name}.jsonl`);
	return existsSync(file)
		? readFileSync(file, "utf8")
				.split("\n")
				.filter((line) => line !== "")
				.map(parseJson)
		: [];
}

const scenario = process.env.PI_HOST_SCENARIO;
if (scenario !== undefined && scenario !== "") {
	const cwd = process.env.PI_RUNNER_CWD;
	assert.ok(cwd !== undefined && cwd !== "");
	const sessions: unknown = JSON.parse(scenario);
	assert.ok(typeof sessions === "object" && sessions !== null);
	const behaviourOf = (slug: string): unknown => field(sessions, slug) ?? "submit";
	mkdirSync(nodePath.join(cwd, "captured"), { recursive: true });
	const capture = (name: string, value: unknown) => {
		appendFileSync(nodePath.join(cwd, "captured", `${name}.jsonl`), `${JSON.stringify(value)}\n`);
	};

	const change = new URL("../../../main/resources/agent/pi-change.ts", import.meta.url).href;
	const derived: unknown = await import(change);
	assert.ok(typeof derived === "object" && derived !== null);
	mock.module(change, {
		namedExports: {
			...derived,
			writeChangeView: () => {
				capture("change-view", true);
			},
		},
	});
	const realFetch = globalThis.fetch;
	mock.method(globalThis, "fetch", async (input: string | URL | Request, init?: RequestInit) => {
		const url = input instanceof Request ? input.url : String(input);
		if (!url.endsWith("/admit-results")) {
			return realFetch(input, init);
		}
		assert.ok(typeof init?.body === "string");
		capture("admission", {
			body: parseJson(init.body),
			reviewInputPresent: existsSync(nodePath.join(cwd, "work/review-input")),
		});
		return Response.json({
			schemaVersion: 2,
			admissionDigest: "digest",
			results: [],
			withheld: [],
		});
	});

	const manager = {
		appendCustomEntry: () => undefined,
		getSessionFile: () => undefined,
		getSessionId: () => "host-session",
		buildSessionProjection: () => ({ messages: [] }),
	};
	interface CustomTool {
		name: string;
		prepareArguments?: (args: unknown) => unknown;
		execute: (id: string, input: unknown) => Promise<unknown>;
	}
	let sessionCount = 0;
	mock.module("@earendil-works/pi-coding-agent", {
		namedExports: {
			defineTool: (tool: unknown) => tool,
			estimateTokens: (message: { content: string }) => Math.ceil(message.content.length / 4),
			createCodemodeExtension: () => () => undefined,
			getAgentDir: () => cwd,
			DefaultResourceLoader: class {
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
			async createAgentSession(options: { customTools: CustomTool[] }) {
				sessionCount += 1;
				const session = sessionCount;
				const handlers = new Set<(event: unknown) => void>();
				const emit = (event: unknown) => {
					for (const handler of handlers) {
						handler(event);
					}
				};
				const submit = options.customTools.find((tool) => tool.name === "submit_result");
				assert.ok(submit, "submit_result is registered");
				/** The practice of this session, as its first turn names it; a later turn need not repeat it. */
				let sessionSlug: string | undefined;
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
						steer: async () => undefined,
						async prompt(
							text: string,
							promptOptions?: { preflightResult?: (disposition: string) => void },
						) {
							promptOptions?.preflightResult?.("started");
							sessionSlug ??= /Submit one result for (?<slug>[a-z0-9-]+) with submit_result/u.exec(
								text,
							)?.groups?.slug;
							const slug = sessionSlug;
							assert.ok(slug !== undefined, "the session's first turn names its practice");
							const behaviour = behaviourOf(slug);
							capture("prompts", {
								session,
								slug,
								reviewInput: existsSync(nodePath.join(cwd, `work/review-input/${slug}.json`)),
							});
							if (behaviour === "hang") {
								await Promise.withResolvers<never>().promise;
							}
							// The model reads the record and answers, as a native call the proxy bounds.
							emit({
								type: "tool_execution_start",
								toolCallId: `read-${session}`,
								toolName: "read",
								args: { path: "evidence/metadata.json" },
							});
							emit({
								type: "tool_execution_end",
								toolCallId: `read-${session}`,
								toolName: "read",
								result: { content: [{ type: "text", text: "[L1] {}" }], details: {} },
								isError: false,
							});
							const submits = behaviour === "submit";
							emit({
								type: "message_end",
								message: {
									role: "assistant",
									stopReason: submits ? "toolUse" : "stop",
									usage: {
										input: 1000,
										output: 100,
										cacheRead: 0,
										cacheWrite: 0,
										cost: { total: 0 },
									},
									content: submits
										? [
												{
													type: "toolCall",
													id: `submit-${session}`,
													name: "submit_result",
													arguments: {},
												},
											]
										: [{ type: "text", text: "Done." }],
								},
							});
							emit({ type: "turn_end" });
							if (submits) {
								const candidate: unknown = hasText(process.env.PI_HOST_SESSION_CANDIDATE)
									? parseJson(process.env.PI_HOST_SESSION_CANDIDATE)
									: SESSION_CANDIDATE;
								const reply = await submit.execute(
									`submit-${session}`,
									submit.prepareArguments?.(candidate) ?? candidate,
								);
								capture("submitted", { slug, reply });
							}
						},
					},
				};
			},
		},
	});
	await import("../../../main/resources/agent/pi-runner.ts");
}
