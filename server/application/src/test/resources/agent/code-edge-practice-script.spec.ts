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

function edge(file: string) {
	return { file, line: 1, endLine: 1 };
}

function declaration(file: string, line: number, language: string, exported: boolean) {
	return [
		{ change: file, line },
		exported ? "public-declaration" : "internal-declaration",
		{ language, exported },
	];
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

void test("the crash practice finds a written operator in each other language it names", async () => {
	const change = new Map([
		added("App/AppDelegate.m", ['NSAssert(window != nil, @"no window");']),
		added("app/src/main/kotlin/Profile.kt", ["val name = user!!.name"]),
		added("cli/main.ts", ["process.exit(1);"]),
		added("cmd/serve.go", ["\tpanic(err)"]),
		added("lib/runner.rb", ['abort "missing config"']),
		added("server/jobs.py", ["sys.exit(1)"]),
		added("src/App/Program.cs", ["Environment.Exit(1);"]),
		added("src/lib.rs", ["let port = text.parse::<u16>().unwrap();"]),
		added("src/main.c", ["abort();"]),
		added("src/main/java/app/Main.java", ["System.exit(1);"]),
		added("web/run.js", ["process.exit(2);"]),
	]);
	const result = await runBundled("avoids-unsafe-panics-and-chosen-crashes", { change });
	const keyOf = await leadKeys("avoids-unsafe-panics-and-chosen-crashes");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [
			lead.at,
			keyOf(lead.label),
			lead.facts?.language,
			lead.facts?.operators,
		]),
		[
			[{ file: "App/AppDelegate.m", line: 1 }, "crash-operator", "objective-c", "NSAssert"],
			[
				{ file: "app/src/main/kotlin/Profile.kt", line: 1 },
				"crash-operator",
				"kotlin",
				"!! force non-null",
			],
			[{ file: "cli/main.ts", line: 1 }, "crash-operator", "typescript", "process.exit"],
			[{ file: "cmd/serve.go", line: 1 }, "crash-operator", "go", "panic("],
			[{ file: "lib/runner.rb", line: 1 }, "crash-operator", "ruby", "abort"],
			[{ file: "server/jobs.py", line: 1 }, "crash-operator", "python", "sys.exit"],
			[{ file: "src/App/Program.cs", line: 1 }, "crash-operator", "csharp", "Environment.Exit"],
			[{ file: "src/lib.rs", line: 1 }, "crash-operator", "rust", ".unwrap()"],
			[{ file: "src/main.c", line: 1 }, "crash-operator", "c", "abort("],
			[{ file: "src/main/java/app/Main.java", line: 1 }, "crash-operator", "java", "System.exit"],
			[{ file: "web/run.js", line: 1 }, "crash-operator", "javascript", "process.exit"],
		],
	);
});

void test("the crash practice reads no language it does not name, and still asks for the review", async () => {
	const { review, candidate } = await runBundledReview("avoids-unsafe-panics-and-chosen-crashes", {
		change: new Map([
			added("scripts/save.lua", ["os.exit(1)"]),
			added("web/index.php", ["exit(1);"]),
		]),
	});
	assert.deepEqual(review.leads ?? [], []);
	assert.deepEqual(review.facts, { crashOperatorLines: 0, inPreview: 0, inTests: 0 });
	// The review decides: an unread file is no proof that the practice does not apply.
	assert.deepEqual(candidate, REVIEWED);
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

void test("the input practice finds a written construct in each other language it names", async () => {
	const change = new Map([
		added("app/src/main/kotlin/Port.kt", ["val port = raw.toInt()"]),
		added("cmd/port.go", ["port, err := strconv.Atoi(raw)"]),
		added("lib/config.rb", ["port = settings.fetch(:port)"]),
		added("server/jobs.py", ["count = int(raw)"]),
		added("src/App/Port.cs", ["var port = int.Parse(raw);"]),
		added("src/lib.rs", ["let share = total / count;"]),
		added("src/main.c", ["int port = atoi(raw);"]),
		added("src/main/java/app/Port.java", ["int port = Integer.parseInt(raw);"]),
		added("web/form.js", ["const qty = parseInt(text, 10);"]),
	]);
	const result = await runBundled("validates-inputs-and-edge-cases-at-the-boundary", { change });
	const keyOf = await leadKeys("validates-inputs-and-edge-cases-at-the-boundary");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at, lead.facts?.language, lead.facts?.constructs]),
		[
			[edge("app/src/main/kotlin/Port.kt"), "kotlin", "toInt/toDouble parse"],
			[edge("cmd/port.go"), "go", "strconv parse"],
			[edge("lib/config.rb"), "ruby", ".fetch"],
			[edge("server/jobs.py"), "python", "int()/float() parse"],
			[edge("src/App/Port.cs"), "csharp", "int.Parse/Convert"],
			[edge("src/lib.rs"), "rust", "division or remainder by a name"],
			[edge("src/main.c"), "c", "atoi/strtol parse"],
			[edge("src/main/java/app/Port.java"), "java", "Integer/Long/Double.parse"],
			[edge("web/form.js"), "javascript", "parseInt/parseFloat"],
		],
	);
	assert.ok(
		(result.leads ?? []).every((lead) => keyOf(lead.label) === "edge-construct"),
		"every lead is an edge construct",
	);
});

void test("the input practice reads no language it does not name, and still asks for the review", async () => {
	const { review, candidate } = await runBundledReview(
		"validates-inputs-and-edge-cases-at-the-boundary",
		{
			change: new Map([
				added("scripts/load.lua", ["local port = tonumber(raw)"]),
				added("web/index.php", ["$port = intval($raw);"]),
			]),
		},
	);
	assert.deepEqual(review.leads ?? [], []);
	assert.equal(review.facts?.edgeConstructLines, 0);
	// The review decides: an unread file is no proof that the practice does not apply.
	assert.deepEqual(candidate, REVIEWED);
});

void test("the public API practice reads the export rule of each language it names", async () => {
	const change = new Map([
		added("Sources/Kit/Store.swift", ["public func load() {}", "func cache() {}"]),
		added("app/src/main/kotlin/Store.kt", ["fun load() {}", "private fun cache() {}"]),
		added("server/store.py", ["def load():", "    pass", "def _cache():", "    pass"]),
		added("src/main/java/app/Store.java", [
			"public class Store {",
			"    void cache() {",
			"    }",
			"}",
		]),
		added("src/store.ts", ["export function load() {}", "function cache() {}"]),
		added("store/store.go", ["func Load() {}", "func cache() {}"]),
		added("web/store.js", ["export const load = () => {};", "const cache = new Map();"]),
	]);
	const result = await runBundled("documents-public-api-and-behaviour-changes", { change });
	const keyOf = await leadKeys("documents-public-api-and-behaviour-changes");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at, keyOf(lead.label), lead.facts]),
		[
			declaration("Sources/Kit/Store.swift", 1, "swift", true),
			declaration("Sources/Kit/Store.swift", 2, "swift", false),
			declaration("app/src/main/kotlin/Store.kt", 1, "kotlin", true),
			declaration("app/src/main/kotlin/Store.kt", 2, "kotlin", false),
			declaration("server/store.py", 1, "python", true),
			declaration("server/store.py", 3, "python", false),
			declaration("src/main/java/app/Store.java", 1, "java", true),
			declaration("src/main/java/app/Store.java", 2, "java", false),
			declaration("src/store.ts", 1, "typescript", true),
			declaration("src/store.ts", 2, "typescript", false),
			declaration("store/store.go", 1, "go", true),
			declaration("store/store.go", 2, "go", false),
			declaration("web/store.js", 1, "javascript", true),
			declaration("web/store.js", 2, "javascript", false),
		],
	);
});

void test("the public API practice reads no language it does not name, and still asks for the review", async () => {
	const { review, candidate } = await runBundledReview(
		"documents-public-api-and-behaviour-changes",
		{
			change: new Map([
				added("lib/store.rb", ["def load; end"]),
				added("src/store.rs", ["pub fn load() {}"]),
			]),
		},
	);
	assert.deepEqual(review.leads ?? [], []);
	assert.equal(review.facts?.filesScanned, 0);
	// The review decides: an unread file is no proof that the practice does not apply.
	assert.deepEqual(candidate, REVIEWED);
});

void test("the clean-code practice finds a debug call in each language it names", async () => {
	const change = new Map([
		added("App/Store.swift", ['debugPrint("loaded")']),
		added("app/src/main/kotlin/Feed.kt", ['println("loaded")']),
		added("cmd/serve.go", ['fmt.Println("loaded")']),
		added("lib/store.rb", ['puts "loaded"']),
		added("server/store.py", ["breakpoint()"]),
		added("src/main.rs", ["dbg!(order);"]),
		added("src/main/java/app/Store.java", ['System.out.println("loaded");']),
		added("src/store.ts", ["debugger;"]),
		added("web/store.js", ['console.log("loaded");']),
	]);
	const result = await runBundled("leaves-the-code-clean-with-intent-revealing-comments", {
		change,
	});
	const keyOf = await leadKeys("leaves-the-code-clean-with-intent-revealing-comments");
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at, keyOf(lead.label), lead.facts?.language]),
		[
			[{ file: "App/Store.swift", line: 1, endLine: 1 }, "debug-output", "swift"],
			[{ file: "app/src/main/kotlin/Feed.kt", line: 1, endLine: 1 }, "debug-output", "kotlin"],
			[{ file: "cmd/serve.go", line: 1, endLine: 1 }, "debug-output", "go"],
			[{ file: "lib/store.rb", line: 1, endLine: 1 }, "debug-output", "ruby"],
			[{ file: "server/store.py", line: 1, endLine: 1 }, "debug-output", "python"],
			[{ file: "src/main.rs", line: 1, endLine: 1 }, "debug-output", "rust"],
			[{ file: "src/main/java/app/Store.java", line: 1, endLine: 1 }, "debug-output", "java"],
			[{ file: "src/store.ts", line: 1, endLine: 1 }, "debug-output", "typescript"],
			[{ file: "web/store.js", line: 1, endLine: 1 }, "debug-output", "javascript"],
		],
	);
	assert.equal(result.facts?.debugCandidates, 9);
});

void test("the clean-code practice reads markers in a language with no debug row, no file of an unknown language, and still asks for the review", async () => {
	const { review, candidate } = await runBundledReview(
		"leaves-the-code-clean-with-intent-revealing-comments",
		{
			change: new Map([
				added("scripts/save.lua", ['print("saved") -- TODO: remove']),
				added("src/App/Store.cs", ['Console.WriteLine("loaded");', "// TODO: remove"]),
			]),
		},
	);
	const keyOf = await leadKeys("leaves-the-code-clean-with-intent-revealing-comments");
	assert.deepEqual(
		(review.leads ?? []).map((lead) => [lead.at, keyOf(lead.label), lead.facts?.language]),
		[[{ file: "src/App/Store.cs", line: 2, endLine: 2 }, "todo-fixme-marker", "csharp"]],
	);
	assert.equal(review.facts?.debugCandidates, 0);
	// The review decides: an unread file is no proof that the practice does not apply.
	assert.deepEqual(candidate, REVIEWED);
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

void test("the untrusted-input practice pairs a source and a sink in each other language it names, and the shared rows in any other", async () => {
	const result = await runBundled("validates-and-escapes-untrusted-input", {
		change: new Map([
			added("src/main/java/app/Run.java", [
				'String name = servletRequest.getParameter("name");',
				"new ProcessBuilder(name).start();",
			]),
			added("web/page.js", ["const page = location.search;", "el.innerHTML = page;"]),
			added("cmd/files.go", ['name := r.FormValue("name")', "path := filepath.Join(root, name)"]),
			// The shared params row is read first, so a Rails params read is reported by that row.
			added("lib/convert.rb", ["name = params[:name]", 'system("convert #{name}")']),
			added("web/hello.php", ["$name = $_GET['name'];", "echo $name;"]),
			added("src/App/Page.cs", ['var name = Request.Query["name"];', "var html = Html.Raw(name);"]),
			added("scripts/open.lua", ["local path = ENV.HOME", "io.popen(path)"]),
		]),
	});
	assert.deepEqual(
		(result.leads ?? []).map((lead) => [lead.at, lead.facts?.sourceToken, lead.facts?.sinkToken]),
		[
			[
				{ change: "src/main/java/app/Run.java", line: 2 },
				"servlet getParameter/getHeader",
				"Runtime.exec / ProcessBuilder",
			],
			[
				{ change: "web/page.js", line: 2 },
				"URL/searchParams",
				"innerHTML / dangerouslySetInnerHTML",
			],
			[{ change: "cmd/files.go", line: 2 }, "FormValue/Query", "path join with input"],
			[{ change: "lib/convert.rb", line: 2 }, "params/query/body/headers/cookies", "exec / shell"],
			[{ change: "web/hello.php", line: 2 }, "superglobals", "echo/print to HTML"],
			[{ change: "src/App/Page.cs", line: 2 }, "request/req param", "Html.Raw"],
			[{ change: "scripts/open.lua", line: 2 }, "env var", "exec / shell"],
		],
	);
});

void test("the public API practice detects the product manifest of each ecosystem it names", async () => {
	const manifests: [string, string, number][] = [
		["Package.swift", '.library(name: "Kit", targets: ["Kit"])', 1],
		["Package.swift", '.executable(name: "App", targets: ["App"])', 0],
		["package.json", '{"name":"kit","main":"index.js"}', 1],
		["package.json", '{"name":"app","private":true,"main":"index.js"}', 0],
		["pom.xml", "<project><packaging>jar</packaging></project>", 1],
		["pom.xml", "<project><packaging>pom</packaging></project>", 0],
		["build.gradle.kts", "plugins { `java-library` }", 1],
		["build.gradle.kts", "plugins { application }", 0],
		["build.gradle", "apply plugin: 'maven-publish'", 1],
		["build.gradle", "apply plugin: 'application'", 0],
		["setup.py", "setup(name='kit', packages=find_packages())", 1],
		["setup.py", "setup(name='app', scripts=['run'])", 0],
		["setup.cfg", "[metadata]\nname = kit\n[options]\npackages = find:\n", 1],
		["setup.cfg", "[metadata]\nname = app\n", 0],
		["pyproject.toml", '[project]\nname = "kit"\n', 1],
		["pyproject.toml", "[tool.black]\nline-length = 100\n", 0],
		["go.mod", "module example.org/kit\n\ngo 1.22\n", 1],
		// A manifest of an ecosystem the script does not name is not read as a product.
		["Cargo.toml", '[package]\nname = "kit"\n', 0],
	];
	const detected = [];
	for (const [file, text] of manifests) {
		const result = await runBundled("documents-public-api-and-behaviour-changes", {
			repo: { [file]: text },
		});
		detected.push([file, text, result.facts?.hasPublicProduct]);
	}
	assert.deepEqual(detected, manifests);
	const unread = await runBundled("documents-public-api-and-behaviour-changes", {
		repo: { "Cargo.toml": '[package]\nname = "kit"\n' },
	});
	// No detected product is no proof of no public API: the review is asked to look.
	assert.match((unread.directions ?? []).join(" "), /Investigate whether this repo exposes/u);
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
