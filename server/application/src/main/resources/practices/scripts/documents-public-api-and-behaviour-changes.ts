// Finds two facts that a hunk alone does not show: if a manifest of the repository declares a library
// or framework product, and if each declaration that the change adds is exported or internal. Neither
// fact settles the public surface. An app without a library product can still expose an HTTP,
// configuration or wire contract to other consumers, which the review investigates. A Swift
// declaration without a modifier is internal, not public. To add a language, add a row.
import { noMatchDirections } from "../lib/bounded-text.ts";
import { isCommentLine } from "../lib/declarations.ts";
import { languageOf } from "../lib/languages.ts";
import {
	definePracticeScript,
	expectKind,
	requireChange,
	type FileTree,
	type ReviewLead,
} from "../lib/practice-script.ts";

export const LABELS = {
	"public-declaration":
		"A declaration that the change adds and that the rules of its language export.",
	"internal-declaration":
		"A declaration that the change adds and that the rules of its language keep internal or private.",
};

// ── (1) Public-product manifest scan ──────────────────────────────────────────────────────────────────────
// Each manifest filename maps to a predicate over its raw text that answers "does this declare a consumable
// library/framework product?". One row per ecosystem; the predicate stays neutral (a structural test, no observation).
const PRODUCT_MANIFESTS: [RegExp, (text: string) => boolean][] = [
	// Swift Package Manager: a `.library(...)` product.
	[/(?:^|\/)Package\.swift$/u, (t) => /\.library\s*\(/u.test(t)],
	// npm: a non-private package that declares an entry/types surface for consumers.
	[
		/(?:^|\/)package\.json$/u,
		(t) =>
			!/"private"\s*:\s*true/u.test(t) && /"(?:main|module|exports|types|typings)"\s*:/u.test(t),
	],
	// Maven: packaged as a real artifact (jar/etc.), not an aggregator pom.
	[/(?:^|\/)pom\.xml$/u, (t) => !/<packaging>\s*pom\s*<\/packaging>/u.test(t)],
	// Gradle: applies the java-library plugin or a publishing plugin.
	[
		/(?:^|\/)build\.gradle(?:\.kts)?$/u,
		(t) =>
			/(?:java-library|maven-publish|`maven-publish`|com\.vanniktech\.maven\.publish)/u.test(t),
	],
	// Python: declares packages to distribute.
	[/(?:^|\/)setup\.py$/u, (t) => /(?:packages\s*=|find_packages\s*\()/u.test(t)],
	[/(?:^|\/)setup\.cfg$/u, (t) => /\[options\][^[]*packages\s*=/u.test(t)],
	[/(?:^|\/)pyproject\.toml$/u, (t) => /(?:\[project\]|\[tool\.poetry\]|packages\s*=)/u.test(t)],
	// Go: a module declaration makes the package importable by others.
	[/(?:^|\/)go\.mod$/u, (t) => /^module\s+\S+/mu.test(t)],
];

const MANIFEST_NAMES = ["swift", "json", "xml", "gradle", "kts", "py", "cfg", "toml", "mod"];

// ── (2) Per-language export status of a CHANGED declaration ────────────────────────────────────────────────
// For each language: detect that a line declares a symbol, and whether that symbol is PUBLIC (exported) or not.
// `isPublic` is only consulted when `decl` matches. Modifier-less Swift is INTERNAL, not public.
interface ExportRule {
	// line introduces a named declaration (func/class/type/var/const)
	decl: RegExp;
	// is that declaration part of the public surface?
	isPublic: (line: string) => boolean;
}

const EXPORT_RULES: Record<string, ExportRule> = {
	// Swift: public/open are exported; modifier-less / internal / private / fileprivate are NOT public.
	swift: {
		decl: /\b(?:func|class|struct|enum|protocol|extension|var|let|typealias|actor|init)\b/u,
		isPublic: (l) => /\b(?:public|open)\b/u.test(l),
	},
	// TypeScript and JavaScript: an `export` keyword (or `export default`) marks the public surface.
	typescript: {
		decl: /\b(?:function|class|interface|type|enum|const|let|var|namespace)\b/u,
		isPublic: (l) => /\bexport\b/u.test(l),
	},
	javascript: {
		decl: /\b(?:function|class|const|let|var)\b/u,
		isPublic: (l) => /\bexport\b/u.test(l),
	},
	// Java: only `public` declarations are part of the API; package-private/protected/private are not.
	java: {
		decl: /\b(?:class|interface|enum|record|void|[A-Z][A-Za-z0-9_<>[\]]*)\s+[A-Za-z_]\w*\s*[({]/u,
		isPublic: (l) => /\bpublic\b/u.test(l),
	},
	// Kotlin: declarations are public by default; private/internal/protected demote them.
	kotlin: {
		decl: /\b(?:fun|class|interface|object|val|var)\b/u,
		isPublic: (l) => !/\b(?:private|internal|protected)\b/u.test(l),
	},
	// Python: a leading-underscore name is private by convention; anything else is public.
	python: {
		decl: /^\s*(?:def|class)\s+[A-Za-z_]/u,
		isPublic: (l) => !/^\s*(?:def|class)\s+_/u.test(l),
	},
	// Go: an exported identifier starts with an upper-case letter.
	go: {
		decl: /^\s*(?:func|type|var|const)\s+[A-Za-z_]/u,
		isPublic: (l) => /^\s*(?:func|type|var|const)\s+(?:\([^)]*\)\s*)?[A-Z]/u.test(l),
	},
};

// (1) Does the repo declare a public library/framework product?
async function declaresPublicProduct(tree: FileTree): Promise<boolean> {
	for (const ext of MANIFEST_NAMES) {
		const { paths } = await tree.list(`**/*.${ext}`);
		for (const manifestPath of paths) {
			const matcher = PRODUCT_MANIFESTS.find(([re]) => re.test(manifestPath));
			if (!matcher) {
				continue;
			}
			const file = await tree.read(manifestPath);
			const text = file.status === "FOUND" ? file.text : "";
			if (matcher[1](text)) {
				return true;
			}
		}
	}
	return false;
}

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const change = requireChange(ctx.files);
		const head = ctx.files?.head ?? null;
		const hasPublicProduct = head !== null && (await declaresPublicProduct(head));

		// (2) Classify each declaration ADDED in the diff as public (exported) or internal.
		const leads: ReviewLead[] = [];
		let changedPublicSymbols = 0;
		let changedInternalSymbols = 0;
		let filesScanned = 0;
		let linesAdded = 0;
		for (const [path, df] of change) {
			const lang = languageOf(path);
			if (lang === null) {
				continue;
			}
			const rule = EXPORT_RULES[lang];
			if (!rule) {
				continue;
			}
			filesScanned += 1;
			for (const [line, content] of df.addedLines) {
				if (isCommentLine(content, lang)) {
					continue;
				}
				linesAdded += 1;
				if (!rule.decl.test(content)) {
					continue;
				}
				const isPublic = rule.isPublic(content);
				if (isPublic) {
					changedPublicSymbols += 1;
				} else {
					changedInternalSymbols += 1;
				}
				if (leads.length < 40) {
					leads.push({
						at: { change: path, line },
						label: LABELS[isPublic ? "public-declaration" : "internal-declaration"],
						facts: { language: lang, exported: isPublic },
					});
				}
			}
		}

		const directions: string[] = [];
		if (!hasPublicProduct) {
			directions.push(
				"No public library/framework product was detected in the repo manifests (no SwiftPM .library, non-private package.json entry, publishable Maven/Gradle artifact, distributable Python package, or Go module).",
				"Investigate whether this repo exposes any public API surface a consumer depends on before deciding documentation was owed.",
			);
		}
		if (changedPublicSymbols > 0) {
			directions.push(
				`The diff adds ${changedPublicSymbols} exported/public declaration(s)${hasPublicProduct ? " in a repo that ships a public product" : ""} — investigate whether their documented behaviour or signature changed and whether that change is reflected in docs/comments.`,
			);
		}
		if (changedInternalSymbols > 0 && changedPublicSymbols === 0) {
			directions.push(
				`The diff adds ${changedInternalSymbols} declaration(s) but none are exported/public (e.g. modifier-less Swift is internal, non-exported TS, package-private Java, leading-underscore Python).`,
				"Investigate whether any public-facing behaviour actually changed before expecting public-API documentation.",
			);
		}
		if (leads.length === 0) {
			directions.push(...noMatchDirections(linesAdded, filesScanned));
		}

		return ctx.review({
			leads,
			facts: {
				hasPublicProduct: hasPublicProduct ? 1 : 0,
				changedPublicSymbols,
				changedInternalSymbols,
				filesScanned,
				linesAdded,
			},
			directions,
		});
	},
});
