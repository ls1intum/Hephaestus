// Finds each color expression that the change adds inside SwiftUI view types, under the first shape
// that its text shows. For a named asset color, it reads from the colorset if the color has a dark
// appearance. A shape is spelling, not a type: `.green` given to `foregroundStyle` is SwiftUI's
// standard color, `Color(.green)` converts UIKit's fixed `UIColor.green`, and a name that the app
// defines is what its definition says. Only the shapes whose spelling fixes the kind are counted as
// literal or adaptive. The review classifies the rest, and reads the role of each color from the
// modifier chain.
import { readFile } from "node:fs/promises";
import path from "node:path";

import { factsOf, noMatchDirections } from "../lib/bounded-text.ts";
import { globFilesSync } from "../lib/files.ts";
import { isJsonObject } from "../lib/json.ts";
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";
import { countLabel, sampleNote, scanAddedLines, type SourcePattern } from "../lib/source-scan.ts";

const SWIFTUI_VIEW = /\b(?:View|App|Scene)\b/u;

// The first pattern that matches a line gives its kind, so the order runs from the shapes whose
// spelling fixes the kind to those whose spelling does not.
const COLORS: readonly SourcePattern[] = [
	[
		"fixed-components",
		/\bColor\s*\(\s*(?:red|hue|white|\.sRGB|\.displayP3|hex)\s*:|\bColor\s*\(\s*"#|\bUIColor\s*\(\s*(?:red|white|hue|displayP3Red)\s*:/u,
	],
	["literal-white-or-black", /\b(?:UI)?Color\.(?:white|black)\b|[:(,]\s*\.(?:white|black)\b/u],
	["uikit-color", /\bUIColor\s*\.\s*[a-z]\w*|\bColor\s*\(\s*uiColor:\s*\.[a-z]\w*/u],
	// `Color(_:bundle:)`: the bundle is optional, and without one the app's main bundle is searched.
	["named-asset", /\bColor\s*\(\s*"[^"]+"\s*(?:\)|,\s*bundle\s*:)/u],
	["color-from-a-value", /\bColor\s*\(\s*\.[a-z]\w*/u],
	["semantic-style", /\.(?:primary|secondary|tertiary|quaternary)\b/u],
	["material", /\.(?:ultraThin|thin|regular|thick|ultraThick|bar)Material\b/u],
	["accent", /\.accentColor\b|[:(,]\s*\.tint\b(?!\s*\()/u],
	["color-member", /\bColor\s*\.\s*[a-z]\w*/u],
	[
		"color-modifier-member",
		/\.(?:foregroundStyle|foregroundColor|background|fill|tint|stroke|strokeBorder|border)\s*\(\s*\.[a-z]\w*/u,
	],
];

/** The shapes whose spelling leaves the kind open: a type or a definition decides it. */
const TO_CLASSIFY = [
	"uikit-color",
	"color-from-a-value",
	"color-member",
	"color-modifier-member",
] as const;

/** Whether a colorset carries a dark appearance; "unknown" when it cannot be read as one. */
type DarkAppearance = boolean | "unknown";

function darkAppearanceOf(source: string): DarkAppearance {
	let parsed: unknown;
	try {
		parsed = JSON.parse(source);
	} catch {
		return "unknown";
	}
	if (!isJsonObject(parsed) || !Array.isArray(parsed.colors) || parsed.colors.length === 0) {
		return "unknown";
	}
	// Every entry is read before a missing dark appearance is a fact: one entry of another shape could
	// be the dark one.
	let dark = false;
	for (const entry of parsed.colors) {
		if (!isJsonObject(entry)) {
			return "unknown";
		}
		if (entry.appearances === undefined) {
			continue;
		}
		if (
			!Array.isArray(entry.appearances) ||
			!entry.appearances.every(
				(appearance) =>
					isJsonObject(appearance) &&
					typeof appearance.appearance === "string" &&
					typeof appearance.value === "string",
			)
		) {
			return "unknown";
		}
		dark ||= entry.appearances.some(
			(appearance) =>
				isJsonObject(appearance) &&
				appearance.appearance === "luminosity" &&
				appearance.value === "dark",
		);
	}
	return dark;
}

/** The bundle a `Color("name", bundle:)` names, or null for the main bundle it searches without one. */
function bundleOf(context: string): string | null {
	const bundle = /Color\s*\(\s*"[^"]+"\s*,\s*bundle\s*:\s*(?<bundle>[^,)]+(?:\([^)]*\))?)/u.exec(
		context,
	)?.groups?.bundle;
	return bundle === undefined || /^(?:\.main|Bundle\.main|nil)\s*$/u.test(bundle)
		? null
		: bundle.trim();
}

/** How many colorsets of the checkout are read; a name beyond them is unknown. */
const COLORSETS_READ = 200;

interface AssetColors {
	/** Every colorset path of the checkout by color name, read or not. */
	paths: Map<string, string[]>;
	/** The appearances of the colorsets read, by path. */
	read: Map<string, DarkAppearance>;
}

function colorNameOf(file: string): string {
	return (
		file
			.split("/")
			.at(-2)
			?.replace(/\.colorset$/u, "") ?? ""
	);
}

/**
 * The appearance of a named color, from its one colorset: the only one of exactly that name, else the
 * only one whose name differs in case alone. Two candidates anywhere in the checkout, or one not read,
 * leave it unknown, since nothing here says which target's catalog the view uses.
 */
function appearanceOf({ paths, read }: AssetColors, name: string): DarkAppearance {
	const exact = paths.get(name);
	const candidates =
		exact ??
		[...paths.entries()]
			.filter(([n]) => n.toLowerCase() === name.toLowerCase())
			.flatMap(([, files]) => files);
	const [only] = candidates;
	return candidates.length === 1 && only !== undefined ? (read.get(only) ?? "unknown") : "unknown";
}

async function assetColors(repoPath: string): Promise<AssetColors> {
	let all: string[] = [];
	try {
		all = globFilesSync("**/*.xcassets/**/*.colorset/Contents.json", repoPath);
	} catch {
		// no readable checkout: no colorset read
	}
	const paths = new Map<string, string[]>();
	for (const file of all) {
		const name = colorNameOf(file);
		paths.set(name, [...(paths.get(name) ?? []), file]);
	}
	const read = new Map<string, DarkAppearance>();
	for (const file of all.slice(0, COLORSETS_READ)) {
		let dark: DarkAppearance = "unknown";
		try {
			dark = darkAppearanceOf(await readFile(path.join(repoPath, file), "utf8"));
		} catch {
			// an unreadable colorset is not a fact about its appearances
		}
		read.set(file, dark);
	}
	return { paths, read };
}

export default definePracticeScript({
	meta: {
		version: 1,
		kinds: {
			"fixed-components":
				"An added color made from fixed components, such as RGB, white or hex values.",
			"literal-white-or-black": "An added literal white or black color.",
			"uikit-color": "An added UIKit color, which can be dynamic or fixed.",
			"named-asset":
				"An added color from a named asset, with the facts that tell if its colorset has a dark appearance.",
			"color-from-a-value": "An added Color(.name) that converts another color value.",
			"semantic-style": "An added semantic style, such as .primary or .secondary.",
			material: "An added material, such as .regularMaterial.",
			accent: "An added accent color or tint.",
			"color-member":
				"An added Color member, such as Color.green, that SwiftUI or the app can define.",
			"color-modifier-member": "An added member, such as .pink, that a color modifier gets.",
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const scan = await scanAddedLines(ctx.repo, ctx.change, {
			languages: ["swift"],
			patterns: COLORS,
			scope: SWIFTUI_VIEW,
			onlyInScope: true,
		});
		const assets = await assetColors(ctx.repo);
		const colorsetsFound = [...assets.paths.values()].reduce((sum, files) => sum + files.length, 0);
		const hints = scan.hints.map((hint) => {
			if (hint.pattern !== "named-asset" && hint.pattern !== "color-from-a-value") {
				return hint;
			}
			const name =
				/Color\s*\(\s*"(?<name>[^"]+)"/u.exec(hint.context)?.groups?.name ??
				/Color\s*\(\s*\.(?<name>\w+)/u.exec(hint.context)?.groups?.name ??
				"";
			// Which catalog another bundle, such as a package's `.module`, holds is not read here.
			const bundle = hint.pattern === "named-asset" ? bundleOf(hint.context) : null;
			return {
				...hint,
				flags: {
					...hint.flags,
					asset: name,
					...(bundle === null ? {} : { bundle }),
					hasDarkAppearance: bundle === null ? appearanceOf(assets, name) : "unknown",
				},
			};
		});
		const leads: Lead[] = hints.map((hint) => ({
			at: { change: hint.file, line: hint.line },
			kind: hint.pattern,
			facts: factsOf(hint.flags),
		}));
		const inOtherBundles = hints.filter((hint) => hint.flags.bundle !== undefined).length;
		const literals =
			countLabel(scan, "fixed-components") + countLabel(scan, "literal-white-or-black");
		const toClassify = TO_CLASSIFY.reduce((sum, label) => sum + countLabel(scan, label), 0);
		const systemAdaptive =
			countLabel(scan, "semantic-style") +
			countLabel(scan, "material") +
			countLabel(scan, "accent");
		const namedAssets = countLabel(scan, "named-asset");
		const assetsWithoutDarkAppearance = hints.filter(
			(hint) => hint.flags.hasDarkAppearance === false,
		).length;
		const directions: string[] = [...sampleNote(scan)];
		if (leads.length === 0) {
			directions.push(...noMatchDirections(scan.linesAdded, scan.filesScanned));
		}
		if (literals > 0) {
			directions.push(
				`Found ${literals} literal colors, ${systemAdaptive} system-adaptive colors and ${namedAssets} named asset colors added in view types. For each literal, read the modifier chain. Text over a fill the same view sets is content.`,
				"A literal background or text on a system background can fix one appearance. A translucent scrim over an image is content, not an adaptive screen background.",
			);
		}
		if (toClassify > 0) {
			directions.push(
				`${toClassify} added line(s) name a color whose spelling does not settle its kind — a UIKit color, \`Color(.name)\`, a \`Color\` member or a member given to a color modifier.`,
				"Read the type it resolves to and, for a name the app defines, its definition: SwiftUI's standard colors and UIKit's dynamic colors change with the appearance, UIKit's fixed constants do not.",
			);
		}
		if (scan.hints.length > 0) {
			directions.push(
				"Each line is listed once, under the first shape its text shows: enumerate every color a listed line holds.",
			);
		}
		if (assetsWithoutDarkAppearance > 0) {
			directions.push(
				`${assetsWithoutDarkAppearance} listed named asset color(s) whose colorset has no dark luminosity entry — read its color values and how it is used before judging whether it adapts.`,
			);
		}
		if (inOtherBundles > 0) {
			directions.push(
				`${String(inOtherBundles)} listed named asset color(s) are looked up in a bundle other than the main one: which asset catalog that bundle holds was not resolved, so their dark appearance is unknown until the catalog is located.`,
			);
		}
		if (namedAssets > 0 && colorsetsFound > COLORSETS_READ) {
			directions.push(
				`The checkout holds ${String(colorsetsFound)} colorsets and the first ${String(COLORSETS_READ)} were read: a named color whose colorset was not read is unknown, not missing.`,
			);
		}
		return {
			leads,
			facts: {
				literalColors: literals,
				systemAdaptiveColors: systemAdaptive,
				namedAssetColors: namedAssets,
				colorsToClassify: toClassify,
				assetsWithoutDarkAppearance,
				assetColorsInCheckout: colorsetsFound,
				filesWithoutCheckout: scan.filesWithoutCheckout,
				filesScanned: scan.filesScanned,
				linesAdded: scan.linesAdded,
			},
			directions,
		};
	},
});
