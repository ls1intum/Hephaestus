// Precompute HINTS for validates-inputs-and-edge-cases-at-the-boundary: locate boundary/edge CANDIDATE
// sites ADDED in the diff, across languages. These are places where a missing index/null/parse/division
// guard could live — the LLM decides whether a guard is actually present. General by design: a per-language
// pattern table keyed off the file extension, NOT a single-language scan. Adding a language = adding a row,
// no engine change. NO verdict, severity, or "defect" — facts only.
import type { DiffFile, PullRequestMetadata, Hint } from "../lib/types";

// Cross-language boundary/edge constructs. Most are language-agnostic enough to share, but the table is
// keyed per-language so a row can be tuned without affecting others.
const COMMON: Array<[string, RegExp]> = [
	// indexed access into an array/collection — a[i], a[i + 1], etc. (not a[0]-style obvious literals only:
	// we still surface; the LLM judges). Skip pure type subscripts is best-effort via the digit/ident guard.
	["index access [..]", /[A-Za-z0-9_)\]]\[[^\]]+\]/],
	// division (potential divide-by-zero) — "/ x" not "//" comment and not "/*".
	["division /", /[A-Za-z0-9_)\]]\s*\/(?![/*])\s*[A-Za-z0-9_(]/],
	// modulo (same zero-divisor edge)
	["modulo %", /[A-Za-z0-9_)\]]\s*%\s*[A-Za-z0-9_(]/],
];

// language key -> [human label, regex] of boundary/edge candidate constructs in ADDED code.
const LANG_PATTERNS: Record<string, Array<[string, RegExp]>> = {
	swift: [
		...COMMON,
		["force-unwrap !", /[A-Za-z0-9_)\]]\!(\.|\s|$|\))/],
		["try!", /\btry!/],
		["Int(...) parse", /\bU?Int\d*\s*\(/],
		["Double/Float(...) parse", /\b(Double|Float)\s*\(/],
		["JSONDecoder.decode", /\.decode\s*\(/],
		[".first/.last", /\.(first|last)\b/],
	],
	ts: [
		...COMMON,
		["non-null assertion !", /[A-Za-z0-9_)\]]\![.;)\s]/],
		["parseInt/parseFloat", /\bparse(Int|Float)\s*\(/],
		["Number(...) parse", /\bNumber\s*\(/],
		["JSON.parse", /\bJSON\.parse\s*\(/],
		[".get(...) lookup", /\.get\s*\(/],
	],
	js: [
		...COMMON,
		["parseInt/parseFloat", /\bparse(Int|Float)\s*\(/],
		["Number(...) parse", /\bNumber\s*\(/],
		["JSON.parse", /\bJSON\.parse\s*\(/],
		[".get(...) lookup", /\.get\s*\(/],
	],
	python: [
		...COMMON,
		["int()/float() parse", /\b(int|float)\s*\(/],
		["json.loads", /\bjson\.loads\s*\(/],
		[".pop()/[index]", /\.pop\s*\(/],
		["next(...)", /\bnext\s*\(/],
	],
	go: [
		...COMMON,
		["strconv parse", /\bstrconv\.(Atoi|Parse[A-Za-z]+)\s*\(/],
		["json.Unmarshal", /\bjson\.Unmarshal\s*\(/],
		["map/slice index", /\[[^\]]+\]/],
	],
	java: [
		...COMMON,
		[".get(i) access", /\.get\s*\(/],
		["Integer/Long/Double.parse", /\b(Integer|Long|Double|Float)\.parse[A-Za-z]+\s*\(/],
		["valueOf parse", /\b(Integer|Long|Double|Float)\.valueOf\s*\(/],
		["Optional.get()", /\bOptional[^;]*\.get\s*\(\s*\)/],
		[".charAt/.substring", /\.(charAt|substring)\s*\(/],
	],
	kotlin: [
		...COMMON,
		["!! force non-null", /\!\!(\.|\s|$|\))/],
		["toInt/toDouble parse", /\.to(Int|Long|Double|Float)\s*\(/],
		[".get(i) access", /\.get\s*\(/],
		[".first()/.last()", /\.(first|last)\s*\(\s*\)/],
	],
	rust: [
		...COMMON,
		[".unwrap()", /\.unwrap\s*\(\s*\)/],
		[".expect(", /\.expect\s*\(/],
		["parse::<>()", /\.parse\s*(::<[^>]+>)?\s*\(/],
		["serde from_str", /\bfrom_str\s*\(/],
	],
	ruby: [
		...COMMON,
		["to_i/to_f parse", /\.to_[if]\b/],
		["JSON.parse", /\bJSON\.parse\s*\(/],
		[".fetch/[index]", /\.fetch\s*\(/],
	],
	c: [
		...COMMON,
		["atoi/strtol parse", /\b(atoi|atol|atof|strtol|strtod)\s*\(/],
		["pointer deref *", /(^|[^\w)])\*[A-Za-z_]/],
	],
	csharp: [
		...COMMON,
		["null-forgiving !", /[A-Za-z0-9_)\]]\!\.(?=[A-Za-z_])/],
		["int.Parse/Convert", /\b(int|long|double|float|decimal|Int32|Int64|Convert)\.(Parse|To[A-Za-z]+)\s*\(/],
		["JsonSerializer.Deserialize", /\.Deserialize\s*\(/],
		[".First()/.Last()/[i]", /\.(First|Last|Single)\s*\(/],
	],
};

// extension -> language key. One source of truth for both pattern lookup and comment syntax.
const EXT_LANG: Record<string, string> = {
	swift: "swift",
	m: "swift",
	mm: "swift",
	ts: "ts",
	tsx: "ts",
	mts: "ts",
	cts: "ts",
	js: "js",
	jsx: "js",
	mjs: "js",
	cjs: "js",
	py: "python",
	go: "go",
	java: "java",
	kt: "kotlin",
	kts: "kotlin",
	rs: "rust",
	rb: "ruby",
	c: "c",
	h: "c",
	cc: "c",
	cpp: "c",
	cxx: "c",
	hpp: "c",
	cs: "csharp",
};

// Public/exported function signatures with at least one parameter — the parameters are the boundary the
// function must defend. We surface the signature site; the LLM checks whether the added body guards them.
const SIGNATURE_PATTERNS: Record<string, RegExp> = {
	swift: /\b(public|open)\s+func\s+\w+\s*\([^)]*[A-Za-z_][^)]*\)/,
	ts: /\bexport\s+(async\s+)?function\s+\w+\s*\([^)]*[A-Za-z_][^)]*\)/,
	js: /\bexport\s+(async\s+)?function\s+\w+\s*\([^)]*[A-Za-z_][^)]*\)/,
	python: /^\s*def\s+(?!_)\w+\s*\([^)]*[A-Za-z_][^)]*\)/,
	go: /^func\s+(\([^)]*\)\s*)?[A-Z]\w*\s*\([^)]*[A-Za-z_][^)]*\)/,
	java: /\bpublic\s+[\w<>\[\],?\s]+\s+\w+\s*\([^)]*[A-Za-z_][^)]*\)/,
	kotlin: /\b(public\s+)?fun\s+\w+\s*\([^)]*[A-Za-z_][^)]*\)/,
	rust: /\bpub\s+(async\s+)?fn\s+\w+\s*(<[^>]*>)?\s*\([^)]*[A-Za-z_][^)]*\)/,
	ruby: /^\s*def\s+(?!_)\w+\s*[\(\s][^)]*[A-Za-z_]/,
	c: /^[A-Za-z_][\w\s\*]+\s+\w+\s*\([^)]*[A-Za-z_][^)]*\)\s*\{?$/,
	csharp: /\bpublic\s+[\w<>\[\],?\s]+\s+\w+\s*\([^)]*[A-Za-z_][^)]*\)/,
};

function langOf(path: string): string | null {
	const ext = path.split(".").pop()?.toLowerCase() ?? "";
	return EXT_LANG[ext] ?? null;
}

// Strip the obvious comment forms so we don't flag a pattern that only appears inside a comment.
function isComment(trimmed: string): boolean {
	return trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("*") || trimmed.startsWith("/*");
}

export default async function (_repo: string, diffFiles: Map<string, DiffFile>, _m: PullRequestMetadata) {
	const hints: Hint[] = [];
	const byLang: Record<string, number> = {};
	let signatureSites = 0;

	for (const [path, df] of diffFiles) {
		const lang = langOf(path);
		if (!lang) continue;
		const patterns = LANG_PATTERNS[lang];
		if (!patterns) continue;
		const sigRe = SIGNATURE_PATTERNS[lang];

		for (const [line, content] of df.addedLines) {
			const trimmed = content.trimStart();
			if (isComment(trimmed)) continue;

			// Public/exported signature with parameters: the parameters are an unguarded-input candidate.
			if (sigRe && sigRe.test(content)) {
				hints.push({
					file: path,
					line,
					pattern: `${lang}:exported-fn-params`,
					context: content.trim().slice(0, 160),
					inDiff: true,
					flags: { kind: "parameter-boundary" },
				});
				byLang[lang] = (byLang[lang] ?? 0) + 1;
				signatureSites++;
				continue;
			}

			for (const [name, re] of patterns) {
				if (re.test(content)) {
					hints.push({
						file: path,
						line,
						pattern: `${lang}:${name}`,
						context: content.trim().slice(0, 160),
						inDiff: true,
						flags: { kind: "edge-candidate" },
					});
					byLang[lang] = (byLang[lang] ?? 0) + 1;
					break;
				}
			}
		}
	}

	const directions =
		hints.length > 0
			? [
					`Found ${hints.length} boundary/edge candidate site(s) added across ${Object.keys(byLang).length} language(s) (${signatureSites} of them public/exported-function parameter lists) — investigate whether each index/deref/parse/division has a visible guard for the empty, missing, malformed, or out-of-range case, or whether such a check is absent in the added code.`,
				]
			: [];

	return {
		hints: hints.slice(0, 40),
		metrics: { edgeCandidateSitesAdded: hints.length, exportedSignatureSites: signatureSites, ...byLang },
		directions,
	};
}
