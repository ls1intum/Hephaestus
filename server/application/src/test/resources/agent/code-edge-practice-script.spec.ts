import assert from "node:assert/strict";
import test from "node:test";

import type { DiffFile } from "../../../../../../docker/agents/practice-scripts/kits/scm/types.ts";
import {
	leadKeys,
	REVIEWED,
	runBundled,
	runBundledReview,
} from "../../../../../../docker/agents/practice-scripts/run-bundled.ts";

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

function crash(file: string, line: number, operators: string, inPreview = false) {
	return {
		at: { file, line },
		kind: "crash-operator",
		facts: { language: "swift", operators, inPreview, testFile: file.startsWith("AppTests/") },
	};
}

void test("the crash practice lists the operators the author wrote, with where each runs, and no implicit trap", async () => {
	const result = await runBundled("avoids-unsafe-panics-and-chosen-crashes", {
		change: new Map([
			hunk("App/Views/NoteRowView.swift", 40, [
				" #Preview {",
				"+    let container = try! ModelContainer(for: Note.self, configurations: config)",
				"     return NoteRowView().modelContainer(container)",
				" }",
			]),
			added("App/Services/Movies.swift", movies),
			added("AppTests/ParserTests.swift", ["let item = try! parser.parse(fixture)"]),
		]),
	});
	const keyOf = await leadKeys("avoids-unsafe-panics-and-chosen-crashes");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => ({
			at: lead.at,
			kind: keyOf(lead.label),
			facts: lead.facts,
		})),
		[
			crash("App/Services/Movies.swift", 4, "try!"),
			crash("App/Services/Movies.swift", 6, "force unwrap"),
			crash("App/Views/NoteRowView.swift", 41, "try!", true),
			crash("AppTests/ParserTests.swift", 1, "try!"),
		],
	);
	const directions = (result.directions ?? []).join(" ");
	assert.match(directions, /1 sit inside a preview block and 1 in test files/u);
	assert.doesNotMatch(directions, /deliberate-crash|investigate whether|should handle/u);
});

void test("a preview opener outside the hunk is read from the pinned file", async () => {
	const result = await runBundled("avoids-unsafe-panics-and-chosen-crashes", {
		repo: {
			"App/GameView.swift": [
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
		},
		change: new Map([
			hunk("App/GameView.swift", 5, [
				"     } catch {",
				String.raw`+        fatalError("Could not create ModelContainer: \(error)")`,
				"     }",
			]),
		]),
	});
	assert.deepEqual(result.leads?.[0]?.facts, {
		language: "swift",
		operators: "fatalError",
		inPreview: true,
		testFile: false,
	});
});

void test("the input practice lists edge constructs in code only, and none the language already makes optional", async () => {
	const result = await runBundled("validates-inputs-and-edge-cases-at-the-boundary", {
		change: new Map([
			added("App/Services/Movies.swift", [
				...movies,
				"override open func motionEnded(_ motion: UIEvent.EventSubtype, with event: UIEvent?) {",
				"title = try container.decode(String.self, forKey: .title)",
			]),
			added("AppTests/ParserTests.swift", ['XCTAssertEqual(items[0].name, "A")']),
			added("server/routes.ts", ["const id = req.params.id;"]),
		]),
	});
	const keyOf = await leadKeys("validates-inputs-and-edge-cases-at-the-boundary");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [
			lead.at,
			keyOf(lead.label),
			lead.facts?.language,
			lead.facts?.constructs,
		]),
		[
			[
				{ file: "App/Services/Movies.swift", line: 1, endLine: 1 },
				"edge-construct",
				"swift",
				"subscript or slice",
			],
			[
				{ file: "App/Services/Movies.swift", line: 3, endLine: 3 },
				"edge-construct",
				"swift",
				"division or remainder by a name",
			],
			[
				{ file: "App/Services/Movies.swift", line: 4, endLine: 4 },
				"edge-construct",
				"swift",
				"payload decode",
			],
			[
				{ file: "App/Services/Movies.swift", line: 9, endLine: 9 },
				"edge-construct",
				"swift",
				"division or remainder by a name",
			],
			[
				{ file: "App/Services/Movies.swift", line: 10, endLine: 10 },
				"edge-construct",
				"swift",
				"extreme sentinel",
			],
			[
				{ file: "server/routes.ts", line: 1, endLine: 1 },
				"edge-construct",
				"typescript",
				"request input",
			],
		],
	);
	assert.equal(result.facts?.divisionOrRemainderByAName, 2);
	assert.equal(result.facts.requestInput, 1);
	const directions = (result.directions ?? []).join(" ");
	assert.match(directions, /not a problem/u);
	assert.doesNotMatch(directions, /investigate whether|is absent|exported-function/u);
});

void test("the untrusted-input practice pairs a source with a rendering call, never with a native image template mode or a template constructor", async () => {
	const result = await runBundled("validates-and-escapes-untrusted-input", {
		change: new Map([
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
	});
	const keyOf = await leadKeys("validates-and-escapes-untrusted-input");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [
			lead.at,
			keyOf(lead.label),
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
});

void test("the error practice lists the fallbacks and bare returns its criteria ask to classify", async () => {
	const result = await runBundled("handles-errors-instead-of-swallowing-them", {
		change: new Map([
			added("App/Parsing.swift", ["return Double(normalizedValue) ?? 0"]),
			added("App/Loader.swift", ["guard let data = cache[key] else { return }"]),
			added("App/Plain.swift", ["let total = values.reduce(0, +)"]),
		]),
	});
	const keyOf = await leadKeys("handles-errors-instead-of-swallowing-them");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at, keyOf(lead.label), lead.facts]),
		[
			[
				{ file: "App/Loader.swift", line: 1, endLine: 1 },
				"guard-else-return",
				{ language: "swift" },
			],
			[
				{ file: "App/Parsing.swift", line: 1, endLine: 1 },
				"fallback-on-a-failable-conversion",
				{ language: "swift" },
			],
		],
	);
});

void test("a fixed URL remains a lead, not proof of an unhandled runtime failure", async () => {
	const result = await runBundled("handles-errors-instead-of-swallowing-them", {
		change: new Map([
			added("App/Endpoint.swift", [
				'guard let url = URL(string: "https://example.org/items") else { return }',
			]),
		]),
	});
	assert.equal(result.leads?.length, 1);
	const directions = (result.directions ?? []).join(" ");
	assert.match(directions, /A valid fixed URL has no runtime-dependent parse failure/u);
	assert.match(directions, /already recorded by its handler/u);
});

void test("the error practice finds a written construct in each other language it names", async () => {
	const change = new Map([
		added("app/src/main/kotlin/Settings.kt", [
			"val port = runCatching { text.toInt() }.getOrNull()",
		]),
		added("cmd/clean.go", ["func clean(path string) {", "\t_ = os.Remove(path)", "}"]),
		added("lib/importer.rb", ["begin", "  import_rows", "rescue => error", "end"]),
		added("server/jobs.py", ["try:", "    load()", "except ValueError:", "    pass"]),
		added("src/cache.rs", ["fn clear(path: &Path) {", "    fs::remove_file(path).ok();", "}"]),
		added("src/main/java/app/Loader.java", [
			"try {",
			"    load();",
			"} catch (IOException e) { e.printStackTrace(); }",
		]),
		added("web/sync.js", ["try {", "  sync();", "} catch (error) {}"]),
	]);
	const result = await runBundled("handles-errors-instead-of-swallowing-them", { change });
	const keyOf = await leadKeys("handles-errors-instead-of-swallowing-them");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at, keyOf(lead.label), lead.facts]),
		[
			[
				{ file: "app/src/main/kotlin/Settings.kt", line: 1, endLine: 1 },
				"run-catching-get-or-null",
				{ language: "kotlin" },
			],
			[
				{ file: "cmd/clean.go", line: 2, endLine: 2 },
				"blank-identifier-assignment",
				{ language: "go" },
			],
			[
				{ file: "lib/importer.rb", line: 3, endLine: 3 },
				"rescue-unused-binding",
				{ language: "ruby" },
			],
			[{ file: "server/jobs.py", line: 3, endLine: 4 }, "except-pass", { language: "python" }],
			[{ file: "src/cache.rs", line: 2, endLine: 2 }, "ok-discards-err", { language: "rust" }],
			[
				{ file: "src/main/java/app/Loader.java", line: 3, endLine: 3 },
				"catch-print-stack-trace-only",
				{ language: "java" },
			],
			[{ file: "web/sync.js", line: 3, endLine: 3 }, "empty-catch", { language: "javascript" }],
		],
	);
});

void test("the error practice reads no language it does not name, and still asks for the review", async () => {
	const { review, candidate } = await runBundledReview(
		"handles-errors-instead-of-swallowing-them",
		{ change: new Map([added("scripts/save.lua", ["local ok = pcall(save)"])]) },
	);
	assert.deepEqual(review.leads ?? [], []);
	assert.deepEqual(review.facts, { errorSwallowCandidates: 0, filesScanned: 0, linesSelected: 0 });
	// The review decides: an unread file is no proof that the practice does not apply.
	assert.deepEqual(candidate, REVIEWED);
});

void test("public, private and unknown credential-named values remain inspection leads", async () => {
	const result = await runBundled("avoids-insecure-defaults-and-over-broad-permissions", {
		change: new Map([
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
	});
	assert.equal(result.facts?.insecureDefaultCandidates, 7);
	const keyOf = await leadKeys("avoids-insecure-defaults-and-over-broad-permissions");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at, keyOf(lead.label)]),
		[
			[{ file: "Client.swift", line: 1, endLine: 1 }, "credential-named-literal"],
			[{ file: "Client.swift", line: 2, endLine: 2 }, "credential-named-literal"],
			[{ file: "Client.swift", line: 3, endLine: 3 }, "credential-named-literal"],
			[{ file: "Client.swift", line: 4, endLine: 4 }, "credential-named-value-in-url-path-query"],
			[{ file: "Client.swift", line: 5, endLine: 5 }, "credential-named-value-in-url-path-query"],
			[{ file: "Client.swift", line: 6, endLine: 6 }, "logs-credential-named-value-response-body"],
			[{ file: "Client.swift", line: 7, endLine: 7 }, "logs-credential-named-value-response-body"],
		],
	);
	assert.match((result.directions ?? []).join(" "), /under class 5 of the standard/u);
});
