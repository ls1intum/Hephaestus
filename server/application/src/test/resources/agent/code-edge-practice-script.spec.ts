import assert from "node:assert/strict";
import { rmSync } from "node:fs";
import { mkdir, mkdtemp, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

import type { DiffFile } from "../../../../../../docker/agents/practice-scripts/lib/types.ts";
import { runBundled } from "../../../../../../docker/agents/practice-scripts/run-bundled.ts";

/**
 * A folder with an empty checkout at `repo`, and a run of the script on a pull request with the
 * checkout, the change and the record that a test gives.
 */
async function stage(slug: string) {
	const root = await mkdtemp(path.join(tmpdir(), "code-edge-practice-script-"));
	await mkdir(path.join(root, "repo"));
	return {
		root,
		script: async (
			repo: string,
			change: ReadonlyMap<string, DiffFile>,
			work: Record<string, unknown>,
		) => runBundled(slug, { repo, change, work }),
	};
}

const metadata = {
	pr_number: 1,
	pr_url: "https://example.org/team/project/pull/1",
	repository_full_name: "team/project",
	source_branch: "f",
	target_branch: "main",
	commit_sha: "abc123",
};

/** A file whose added lines are numbered from 1, with no hunk context. */
function added(file: string, lines: string[]): [string, DiffFile] {
	return [
		file,
		{
			path: file,
			addedLines: new Map(lines.map((line, index) => [index + 1, line])),
			removedLines: new Map(),
			hunks: [],
		},
	];
}

/** One hunk from new-side line `start`: `+` lines are added, the rest is context. */
function hunk(file: string, start: number, lines: string[]): [string, DiffFile] {
	const addedLines = new Map<number, string>();
	for (const [index, line] of lines.entries()) {
		if (line.startsWith("+")) {
			addedLines.set(start + index, line.slice(1));
		}
	}
	return [
		file,
		{
			path: file,
			addedLines,
			removedLines: new Map(),
			hunks: [
				{
					oldStart: start,
					oldCount: lines.length - addedLines.size,
					newStart: start,
					newCount: lines.length,
					lines,
				},
			],
		},
	];
}

const movies = [
	"return Array(response.cast[0..<2])",
	"let temp = Int(response.current.temp_c)",
	"let ratio = Double(done) / Double(total)",
	"let movie = try! decoder.decode(Movie.self, from: data)",
	'Text("Device shaken! Try again at https://example.com/help")',
	'let url = URL(string: "https://api.example.com")!',
	"let year = Int(yearText) ?? 0",
	"counts[key, default: 0] += 1",
	"let average = sum / items.count",
	"var oldest = Int.max",
	"let score = raw / 20",
	"if let first = items.first {",
];

/** The lead of one Swift line that spells a crash operator. */
function crash(file: string, line: number, operators: string, inPreview = false) {
	return {
		at: { change: file, line },
		kind: "crash-operator",
		facts: { language: "swift", operators, inPreview, testFile: file.startsWith("AppTests/") },
	};
}

void test("the crash practice lists the operators the author wrote, with where each runs, and no implicit trap", async () => {
	const { root, script } = await stage("avoids-unsafe-panics-and-chosen-crashes");
	try {
		const result = await script(
			path.join(root, "repo"),
			new Map([
				hunk("App/Views/NoteRowView.swift", 40, [
					" #Preview {",
					"+    let container = try! ModelContainer(for: Note.self, configurations: config)",
					"     return NoteRowView().modelContainer(container)",
					" }",
				]),
				added("App/Services/Movies.swift", movies),
				added("AppTests/ParserTests.swift", ["let item = try! parser.parse(fixture)"]),
			]),
			metadata,
		);
		assert.deepEqual(result.leads, [
			crash("App/Services/Movies.swift", 4, "try!"),
			crash("App/Services/Movies.swift", 6, "force unwrap"),
			crash("App/Views/NoteRowView.swift", 41, "try!", true),
			crash("AppTests/ParserTests.swift", 1, "try!"),
		]);
		const directions = (result.directions ?? []).join(" ");
		assert.match(directions, /1 sit inside a preview block and 1 in test files/u);
		assert.doesNotMatch(directions, /deliberate-crash|investigate whether|should handle/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a preview opener outside the hunk is read from the pinned file", async () => {
	const { root, script } = await stage("avoids-unsafe-panics-and-chosen-crashes");
	try {
		await mkdir(path.join(root, "repo/App"));
		await writeFile(
			path.join(root, "repo/App/GameView.swift"),
			[
				"#Preview {",
				"    let container: ModelContainer",
				"    do {",
				"        container = try ModelContainer(for: GameSession.self)",
				"    } catch {",
				String.raw`        fatalError("Could not create ModelContainer: \(error)")`,
				"    }",
				"    return GameView().modelContainer(container)",
				"}",
			].join("\n"),
		);
		const result = await script(
			path.join(root, "repo"),
			new Map([
				hunk("App/GameView.swift", 5, [
					"     } catch {",
					String.raw`+        fatalError("Could not create ModelContainer: \(error)")`,
					"     }",
				]),
			]),
			metadata,
		);
		assert.deepEqual(result.leads[0]?.facts, {
			language: "swift",
			operators: "fatalError",
			inPreview: true,
			testFile: false,
		});
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("the input practice lists edge constructs in code only, and none the language already makes optional", async () => {
	const { root, script } = await stage("validates-inputs-and-edge-cases-at-the-boundary");
	try {
		const result = await script(
			path.join(root, "repo"),
			new Map([
				added("App/Services/Movies.swift", [
					...movies,
					"override open func motionEnded(_ motion: UIEvent.EventSubtype, with event: UIEvent?) {",
					"title = try container.decode(String.self, forKey: .title)",
				]),
				added("AppTests/ParserTests.swift", ['XCTAssertEqual(items[0].name, "A")']),
				added("server/routes.ts", ["const id = req.params.id;"]),
			]),
			metadata,
		);
		assert.deepEqual(
			result.leads.map((lead) => [
				lead.at,
				lead.kind,
				lead.facts?.language,
				lead.facts?.constructs,
			]),
			[
				[
					{ change: "App/Services/Movies.swift", line: 1 },
					"edge-construct",
					"swift",
					"subscript or slice",
				],
				[
					{ change: "App/Services/Movies.swift", line: 3 },
					"edge-construct",
					"swift",
					"division or remainder by a name",
				],
				[
					{ change: "App/Services/Movies.swift", line: 4 },
					"edge-construct",
					"swift",
					"payload decode",
				],
				[
					{ change: "App/Services/Movies.swift", line: 9 },
					"edge-construct",
					"swift",
					"division or remainder by a name",
				],
				[
					{ change: "App/Services/Movies.swift", line: 10 },
					"edge-construct",
					"swift",
					"extreme sentinel",
				],
				[{ change: "server/routes.ts", line: 1 }, "edge-construct", "typescript", "request input"],
			],
		);
		assert.equal(result.facts?.divisionOrRemainderByAName, 2);
		assert.equal(result.facts.requestInput, 1);
		const directions = (result.directions ?? []).join(" ");
		assert.match(directions, /not an observation/u);
		assert.doesNotMatch(directions, /investigate whether|is absent|exported-function/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("the untrusted-input practice pairs a source with a rendering call, never with a native image template mode or a template constructor", async () => {
	const { root, script } = await stage("validates-and-escapes-untrusted-input");
	try {
		const result = await script(
			path.join(root, "repo"),
			new Map([
				added("App/Settings.swift", [
					'let dark = UserDefaults.standard.bool(forKey: "dark")',
					'Image(systemName: "checkmark").renderingMode(.template)',
				]),
				added("App/Greeting.swift", [
					'let name = UserDefaults.standard.string(forKey: "name") ?? ""',
					String.raw`Text("Hello \(name)")`,
				]),
				added("App/Page.swift", [
					'let html = UserDefaults.standard.string(forKey: "page") ?? ""',
					"webView.loadHTMLString(html, baseURL: nil)",
				]),
				added("server/views.py", [
					'name = request.args.get("name")',
					'return render_template_string("Hello " + name)',
				]),
				added("server/compile.ts", [
					"const name = req.query.name;",
					"const template = Handlebars.compile(source);",
				]),
				added("server/page.py", [
					'name = request.args.get("name")',
					'template = jinja2.Template("Hello {{ name }}")',
					"return template.render(name=name)",
				]),
			]),
			metadata,
		);
		assert.deepEqual(
			result.leads.map((lead) => [
				lead.at,
				lead.kind,
				lead.facts?.sourceToken,
				lead.facts?.sinkToken,
			]),
			[
				[
					{ change: "App/Page.swift", line: 2 },
					"source-near-sink",
					"UserDefaults / FileManager / env",
					"WKWebView loadHTMLString / evaluateJavaScript",
				],
				[
					{ change: "server/views.py", line: 2 },
					"source-near-sink",
					"request/req param",
					"template render",
				],
				[
					{ change: "server/page.py", line: 3 },
					"source-near-sink",
					"request/req param",
					"template render",
				],
			],
		);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("the error practice lists the fallbacks and bare returns its criteria ask to classify", async () => {
	const { root, script } = await stage("handles-errors-instead-of-swallowing-them");
	try {
		const result = await script(
			path.join(root, "repo"),
			new Map([
				added("App/Parsing.swift", ["return Double(normalizedValue) ?? 0"]),
				added("App/Loader.swift", ["guard let data = cache[key] else { return }"]),
				added("App/Plain.swift", ["let total = values.reduce(0, +)"]),
			]),
			metadata,
		);
		assert.deepEqual(
			result.leads.map((lead) => [lead.at, lead.kind, lead.facts]),
			[
				[
					{ change: "App/Parsing.swift", line: 1 },
					"fallback-on-a-failable-conversion",
					{ language: "swift" },
				],
				[{ change: "App/Loader.swift", line: 1 }, "guard-else-return", { language: "swift" }],
			],
		);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a fixed URL remains a lead, not proof of an unhandled runtime failure", async () => {
	const { root, script } = await stage("handles-errors-instead-of-swallowing-them");
	try {
		const result = await script(
			path.join(root, "repo"),
			new Map([
				added("App/Endpoint.swift", [
					'guard let url = URL(string: "https://example.org/items") else { return }',
				]),
			]),
			metadata,
		);
		assert.equal(result.leads.length, 1);
		const directions = (result.directions ?? []).join(" ");
		assert.match(directions, /A valid fixed URL has no runtime-dependent parse failure/u);
		assert.match(directions, /already recorded by its handler/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("public, private and unknown credential-named values remain inspection leads", async () => {
	const { root, script } = await stage("avoids-insecure-defaults-and-over-broad-permissions");
	try {
		const result = await script(
			path.join(root, "repo"),
			new Map([
				added("Client.swift", [
					'let apiKey = "shared-public-demo"',
					'let secret = "account-sandbox-credential"',
					'let token = "unclassified-service-value"',
					String.raw`let url = "https://service.example/\(apiKey)/items"`,
					String.raw`let url = "https://service.example/\(secret)/items"`,
					"print(token)",
					"print(response.body)",
				]),
			]),
			metadata,
		);
		assert.equal(result.facts?.insecureDefaultCandidates, 7);
		assert.deepEqual(
			result.leads.map((lead) => [lead.at, lead.kind]),
			[
				[{ change: "Client.swift", line: 1 }, "credential-named-literal"],
				[{ change: "Client.swift", line: 2 }, "credential-named-literal"],
				[{ change: "Client.swift", line: 3 }, "credential-named-literal"],
				[{ change: "Client.swift", line: 4 }, "credential-named-value-in-url-path-query"],
				[{ change: "Client.swift", line: 5 }, "credential-named-value-in-url-path-query"],
				[{ change: "Client.swift", line: 6 }, "logs-credential-named-value-response-body"],
				[{ change: "Client.swift", line: 7 }, "logs-credential-named-value-response-body"],
			],
		);
		assert.match((result.directions ?? []).join(" "), /under class 5 of the standard/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});
