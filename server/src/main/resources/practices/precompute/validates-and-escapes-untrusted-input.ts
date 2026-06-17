// Precompute HINTS for validates-and-escapes-untrusted-input: surface ADDED lines where an untrusted-INPUT
// token (a SOURCE) and a dangerous OPERATION token (a SINK) co-occur in the same file within a small line
// window — i.e. the taint flow a reviewer is most likely to miss by eye. CANDIDATES only: the LLM traces
// whether the source actually reaches the sink unvalidated/unescaped and decides. General by design: a
// per-language SOURCE table + a SINK table keyed off the file extension. Adding a language = adding rows,
// no engine change. This practice has historically gone unfired because the flow spans lines, so we pair
// sources to nearby sinks and hand the LLM the exact span to trace.
import type { DiffFile, PullRequestMetadata, Hint } from "../lib/types";

// Sources of untrusted input (request/CLI/env/file/stdin). Cross-language patterns live under "all" and are
// checked for every file; language-specific rows refine the common web/runtime surfaces.
const SOURCES: Record<string, Array<[string, RegExp]>> = {
	all: [
		["request/req param", /\b(request|req)\b\s*[.[]/i],
		["params/query/body/headers/cookies", /\b(params|query|queryString|body|headers|cookies)\b\s*[.[]/],
		["env var", /\b(process\.env|os\.environ|System\.getenv|getenv|std::env::var|ENV)\b/],
		["argv / CLI args", /\b(argv|sys\.args|os\.Args|process\.argv|CommandLine\.arguments|args\[)\b/],
		["stdin / scanner read", /\b(stdin|readLine|Scanner|BufferedReader|input\s*\(|gets\b)\b/],
	],
	java: [
		["servlet getParameter/getHeader", /\.get(Parameter|Header|QueryString|Cookies|InputStream|Reader)\s*\(/],
		["@RequestParam/@PathVariable/@RequestBody", /@(RequestParam|PathVariable|RequestBody|RequestHeader|CookieValue)\b/],
		["file read", /\bnew\s+(FileReader|FileInputStream)\s*\(|Files\.(read|newInputStream)\b/],
	],
	ts: [
		["express/koa req", /\breq\.(params|query|body|headers|cookies|get)\b/],
		["fs read", /\bfs\.(readFile|readFileSync|createReadStream)\b/],
		["URL/searchParams", /\b(searchParams|URLSearchParams|location\.(search|hash|href))\b/],
	],
	python: [
		["flask/django request", /\brequest\.(args|form|values|json|GET|POST|data|files|headers|cookies)\b/],
		["open() read", /\bopen\s*\([^)]*['"]r/],
	],
	go: [
		["http.Request fields", /\br\.(URL|Form|PostForm|Body|Header|Cookie)\b/],
		["FormValue/Query", /\.(FormValue|Query|PathValue)\s*\(/],
	],
	ruby: [
		["rails params", /\bparams\[/],
	],
	php: [
		["superglobals", /\$_(GET|POST|REQUEST|COOKIE|SERVER|FILES)\b/],
	],
	csharp: [
		["Request fields", /\bRequest\.(Query|Form|Headers|Cookies|Body|QueryString)\b/],
	],
};

// Sinks: dangerous operations that must receive validated/escaped input (SQL, command/eval, markup, path,
// templating, deserialization). Cross-language rows under "all"; language rows refine.
const SINKS: Record<string, Array<[string, RegExp]>> = {
	all: [
		["raw SQL string-concat", /\b(SELECT|INSERT|UPDATE|DELETE|WHERE|FROM)\b[^;]*(\+|\$\{|%s|f["']|`|\|\||\.\.)/i],
		["eval", /\beval\s*\(/],
		["exec / shell", /\b(exec|execSync|execve|spawn|popen|os\.system|subprocess\.(call|run|Popen)|shell_exec|system)\s*\(/],
		["deserialize", /\b(pickle\.loads|yaml\.load\b|Marshal\.load|unserialize|JSON\.parse|deserialize)\s*\(/i],
		["template render", /\b(render(_template)?|template|Mustache|Handlebars|Jinja|ejs)\b/i],
		["path join with input", /\b(path\.join|os\.path\.join|filepath\.Join|Paths\.get|File\s*\()/],
	],
	java: [
		["Runtime.exec / ProcessBuilder", /\b(Runtime\.getRuntime\(\)\.exec|ProcessBuilder)\b/],
		["Statement.execute (no prepare)", /\b(createStatement|Statement)\b[^;]*\.(execute|executeQuery|executeUpdate)\b/],
		["ObjectInputStream", /\bObjectInputStream\b/],
	],
	ts: [
		["innerHTML / dangerouslySetInnerHTML", /\b(innerHTML|outerHTML|dangerouslySetInnerHTML|insertAdjacentHTML|document\.write)\b/],
		["new Function", /\bnew\s+Function\s*\(/],
	],
	python: [
		["cursor.execute concat", /\bcursor\.execute\b/],
		["os.system / subprocess shell=True", /shell\s*=\s*True/],
	],
	php: [
		["echo/print to HTML", /\b(echo|print)\b/],
	],
	csharp: [
		["SqlCommand concat", /\bnew\s+SqlCommand\b/],
		["Html.Raw", /\bHtml\.Raw\s*\(/],
	],
};

// extension -> language key (drives which SOURCES/SINKS rows refine the "all" set).
const EXT_LANG: Record<string, string> = {
	ts: "ts", tsx: "ts", mts: "ts", cts: "ts", js: "ts", jsx: "ts", mjs: "ts", cjs: "ts",
	py: "python",
	java: "java",
	go: "go",
	rb: "ruby",
	php: "php",
	cs: "csharp",
};

// Sources and sinks co-occurring within this many added lines are surfaced as a candidate flow.
const WINDOW = 25;

function langOf(path: string): string | null {
	const ext = path.split(".").pop()?.toLowerCase() ?? "";
	return EXT_LANG[ext] ?? null;
}

function isComment(t: string): boolean {
	return t.startsWith("//") || t.startsWith("#") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("--");
}

function firstMatch(rows: Array<[string, RegExp]>, content: string): string | null {
	for (const [label, re] of rows) {
		if (re.test(content)) return label;
	}
	return null;
}

export default async function (_repo: string, diffFiles: Map<string, DiffFile>, _m: PullRequestMetadata) {
	const hints: Hint[] = [];
	let flowCount = 0;

	for (const [path, df] of diffFiles) {
		const lang = langOf(path);
		const sourceRows = [...SOURCES.all, ...(lang ? (SOURCES[lang] ?? []) : [])];
		const sinkRows = [...SINKS.all, ...(lang ? (SINKS[lang] ?? []) : [])];

		// Collect source / sink positions on ADDED lines only.
		const srcLines: Array<{ line: number; label: string; content: string }> = [];
		const sinkLines: Array<{ line: number; label: string; content: string }> = [];
		for (const [line, content] of df.addedLines) {
			const trimmed = content.trimStart();
			if (isComment(trimmed)) continue;
			const s = firstMatch(sourceRows, content);
			if (s) srcLines.push({ line, label: s, content });
			const k = firstMatch(sinkRows, content);
			if (k) sinkLines.push({ line, label: k, content });
		}
		if (srcLines.length === 0 || sinkLines.length === 0) continue;

		// Surface each sink that has a source within WINDOW lines — that pairing is the flow to trace.
		for (const sink of sinkLines) {
			const near = srcLines.find((s) => Math.abs(s.line - sink.line) <= WINDOW);
			if (!near) continue;
			flowCount++;
			hints.push({
				file: path,
				line: sink.line,
				pattern: `source→sink: ${near.label} → ${sink.label}`,
				context: sink.content.trim().slice(0, 160),
				inDiff: true,
				flags: {
					sourceLine: near.line,
					sourceToken: near.label,
					sinkToken: sink.label,
					lineDistance: Math.abs(near.line - sink.line),
				},
			});
		}
	}

	const directions =
		flowCount > 0
			? [
					`Found ${flowCount} added line(s) where an untrusted-input source (request/params/env/file/stdin/argv) and a dangerous sink (raw SQL concat, exec/eval/system, innerHTML, path join, template render, deserialize) co-occur within ${WINDOW} lines — trace whether the source reaches the sink without validation/escaping/parameterization, and confirm before deciding.`,
				]
			: [];

	return { hints: hints.slice(0, 40), metrics: { sourceSinkFlows: flowCount }, directions };
}
