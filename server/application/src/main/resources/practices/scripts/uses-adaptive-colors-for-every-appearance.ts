import { scm, type FileTree } from "../kits/scm/index.ts";
import {
	countLabel,
	sampleNote,
	scanOccurrences,
	type SourcePattern,
} from "../kits/scm/source-scan.ts";
import { factsOf, noMatchDirections } from "../lib/core/bounded-text.ts";
import { isJsonObject } from "../lib/core/json.ts";
import { definePracticeScript, type ReviewLead } from "../lib/core/practice-script.ts";

const SWIFTUI_VIEW = /\b(?:View|App|Scene)\b/u;

const COLORS: readonly SourcePattern[] = [
	[
		"fixed-components",
		/\bColor\s*\(\s*(?:red|hue|white|\.sRGB|\.displayP3|hex)\s*:|\bColor\s*\(\s*"#|\bUIColor\s*\(\s*(?:red|white|hue|displayP3Red)\s*:/u,
	],
	["literal-white-or-black", /\b(?:UI)?Color\.(?:white|black)\b|[:(,]\s*\.(?:white|black)\b/u],
	["uikit-color", /\bUIColor\s*\.\s*[a-z]\w*|\bColor\s*\(\s*uiColor:\s*\.[a-z]\w*/u],
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

const TO_CLASSIFY = [
	"uikit-color",
	"color-from-a-value",
	"color-member",
	"color-modifier-member",
] as const;

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
	// Validate every colorset entry before treating missing dark appearance as a fact.
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

function bundleOf(context: string): string | null {
	const bundle = /Color\s*\(\s*"[^"]+"\s*,\s*bundle\s*:\s*(?<bundle>[^,)]+(?:\([^)]*\))?)/u.exec(
		context,
	)?.groups?.bundle;
	return bundle === undefined || /^(?:\.main|Bundle\.main|nil)\s*$/u.test(bundle)
		? null
		: bundle.trim();
}

const COLORSETS_READ = 200;

interface AssetColors {
	paths: Map<string, string[]>;
	read: Map<string, DarkAppearance>;
	listing: "complete" | "truncated" | "unavailable";
}

function colorNameOf(file: string): string {
	return (
		file
			.split("/")
			.at(-2)
			?.replace(/\.colorset$/u, "") ?? ""
	);
}

function appearanceOf({ paths, read, listing }: AssetColors, name: string): DarkAppearance {
	if (listing !== "complete") {
		return "unknown";
	}
	const exact = paths.get(name);
	const candidates =
		exact ??
		[...paths.entries()]
			.filter(([n]) => n.toLowerCase() === name.toLowerCase())
			.flatMap(([, files]) => files);
	const [only] = candidates;
	return candidates.length === 1 && only !== undefined ? (read.get(only) ?? "unknown") : "unknown";
}

async function assetColors(tree: FileTree | null): Promise<AssetColors> {
	const listed = await tree
		?.list("**/*.xcassets/**/*.colorset/Contents.json")
		.catch(() => undefined);
	const all = listed?.paths ?? [];
	let listing: AssetColors["listing"] = "unavailable";
	if (listed !== undefined) {
		listing = listed.truncated ? "truncated" : "complete";
	}
	const paths = new Map<string, string[]>();
	for (const file of all) {
		const name = colorNameOf(file);
		paths.set(name, [...(paths.get(name) ?? []), file]);
	}
	const read = new Map<string, DarkAppearance>();
	for (const file of listing === "complete" ? all.slice(0, COLORSETS_READ) : []) {
		const source = await tree?.read(file).catch(() => null);
		read.set(file, source?.status === "FOUND" ? darkAppearanceOf(source.text) : "unknown");
	}
	return { paths, read, listing };
}

export const LABELS = {
	"fixed-components":
		"A selected color made from fixed components, such as RGB, white or hex values.",
	"literal-white-or-black": "A selected literal white or black color.",
	"uikit-color": "A selected UIKit color, which can be dynamic or fixed.",
	"named-asset":
		"A selected color from a named asset, with the facts that tell if its colorset has a dark appearance.",
	"color-from-a-value": "A selected Color(.name) that converts another color value.",
	"semantic-style": "A selected semantic style, such as .primary or .secondary.",
	material: "A selected material, such as .regularMaterial.",
	accent: "A selected accent color or tint.",
	"color-member":
		"A selected Color member, such as Color.green, that SwiftUI or the app can define.",
	"color-modifier-member": "A selected member, such as .pink, that a color modifier gets.",
};
const LABEL_OF = new Map<string, string>(Object.entries(LABELS));

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		if (domain.kind !== "scm.pull_request" && domain.kind !== "scm.file") {
			throw new Error("this practice requires SCM source files");
		}
		const scan = await scanOccurrences(ctx, domain.files, {
			languages: ["swift"],
			patterns: COLORS,
			scope: SWIFTUI_VIEW,
			onlyInScope: true,
		});
		const assets = await assetColors(domain.files?.head ?? null);
		const colorsetsFound = [...assets.paths.values()].reduce((sum, files) => sum + files.length, 0);
		const hints = scan.hints.map((hint) => {
			if (hint.pattern !== "named-asset" && hint.pattern !== "color-from-a-value") {
				return hint;
			}
			const name =
				/Color\s*\(\s*"(?<name>[^"]+)"/u.exec(hint.context)?.groups?.name ??
				/Color\s*\(\s*\.(?<name>\w+)/u.exec(hint.context)?.groups?.name ??
				"";
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
		const leads: ReviewLead[] = hints.flatMap((hint) => {
			const label = LABEL_OF.get(hint.pattern);
			return label === undefined
				? []
				: [
						{
							at: { file: hint.file, line: hint.line, endLine: hint.endLine },
							label,
							facts: factsOf(hint.flags),
						},
					];
		});
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
			directions.push(...noMatchDirections(scan.linesSelected, scan.filesScanned));
		}
		if (literals > 0) {
			directions.push(
				`Found ${literals} literal colors, ${systemAdaptive} system-adaptive colors and ${namedAssets} named asset colors selected in view types. For each literal, read the modifier chain. Text over a fill the same view sets is content.`,
				"A literal background or text on a system background can fix one appearance. A translucent scrim over an image is content, not an adaptive screen background.",
			);
		}
		if (toClassify > 0) {
			directions.push(
				`${toClassify} selected line(s) name a color whose spelling does not settle its kind — a UIKit color, \`Color(.name)\`, a \`Color\` member or a member given to a color modifier.`,
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
		if (namedAssets > 0 && assets.listing !== "complete") {
			directions.push(
				"The colorset inventory is incomplete. An unlisted colorset may share a listed name, so named colors' dark appearance is unknown.",
			);
		} else if (namedAssets > 0 && colorsetsFound > COLORSETS_READ) {
			directions.push(
				`The checkout holds ${String(colorsetsFound)} colorsets and the first ${String(COLORSETS_READ)} were read: a named color whose colorset was not read is unknown, not missing.`,
			);
		}
		return ctx.review({
			leads,
			limitations:
				namedAssets > 0 && assets.listing !== "complete"
					? [
							{
								what: "colorset inventory",
								count: 1,
								reason: assets.listing === "truncated" ? "truncated" : "unavailable",
							},
						]
					: [],
			facts: {
				literalColors: literals,
				systemAdaptiveColors: systemAdaptive,
				namedAssetColors: namedAssets,
				colorsToClassify: toClassify,
				assetsWithoutDarkAppearance,
				assetColorsInCheckout: assets.listing === "complete" ? colorsetsFound : -1,
				filesWithUnknownLocation: scan.filesWithUnknownLocation,
				filesScanned: scan.filesScanned,
				linesSelected: scan.linesSelected,
			},
			directions,
		});
	},
});
