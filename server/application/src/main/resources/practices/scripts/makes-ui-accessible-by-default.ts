// Finds the interface lines that the change adds in Swift files and that the criteria ask about:
// icon-only controls, images, fixed-size fonts, height caps and colour-only state. It also finds the
// accessibility modifiers that the change adds with them. The review reads the view chain.
import { factsOf, noMatchDirections } from "../lib/bounded-text.ts";
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";
import { countLabel, sampleNote, scanAddedLines, type SourcePattern } from "../lib/source-scan.ts";

// Font.custom(_:size:) scales with the body style, custom(_:size:relativeTo:) with the style it names;
// only custom(_:fixedSize:) does not scale. Font.system(size:) is fixed at the size it is given.
const INTERFACE_LINES: readonly SourcePattern[] = [
	[
		"accessibility-modifier",
		/\.accessibility(?:Label|Hint|Hidden|AddTraits|Value|Identifier)\s*\(/u,
	],
	["label-with-text", /\bLabel\s*\(\s*"/u],
	["symbol-image", /\bImage\s*\(\s*systemName:/u],
	["asset-image", /\bImage\s*\(\s*(?:"|decorative:|[a-z]\w*\.)/u],
	["fixed-size-custom-font", /\.custom\s*\([^)]*\bfixedSize:/u],
	["system-font-with-a-point-size", /\.system\s*\(\s*size:/u],
	["scaled-custom-font", /\.custom\s*\([^)]*\bsize:/u],
	[
		"semantic-font",
		/\.font\s*\(\s*\.(?:largeTitle|title[23]?|headline|subheadline|body|callout|footnote|caption2?)\b/u,
	],
	["height-cap", /\.frame\s*\([^)]*\bheight:\s*\d/u],
	["line-limit", /\.lineLimit\s*\(\s*\d/u],
	[
		"colour-by-state",
		/\.foregroundStyle\s*\(\s*[^)]*\?\s*\.(?:red|green|orange|yellow)|\.foregroundColor\s*\(\s*[^)]*\?\s*\.(?:red|green|orange|yellow)/u,
	],
	["button", /\bButton\s*[({]/u],
	["toggle", /\bToggle\s*\(/u],
];

export default definePracticeScript({
	meta: {
		kinds: {
			"accessibility-modifier":
				"An added accessibility modifier, such as accessibilityLabel or accessibilityHidden.",
			"label-with-text": "An added Label with a text title.",
			"symbol-image": "An added Image(systemName:).",
			"asset-image": "An added Image from an asset or a decorative image.",
			"fixed-size-custom-font":
				"An added custom font with fixedSize:, which does not scale with the text size.",
			"system-font-with-a-point-size": "An added system font with a point size.",
			"scaled-custom-font": "An added custom font with size:, which scales with the body style.",
			"semantic-font": "An added semantic font, such as .headline or .body.",
			"height-cap": "An added frame with a fixed height.",
			"line-limit": "An added lineLimit with a number.",
			"colour-by-state":
				"An added foreground colour that a condition selects, such as red or green.",
			button: "An added Button.",
			toggle: "An added Toggle.",
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const scan = await scanAddedLines(ctx.repo, ctx.change, {
			languages: ["swift"],
			patterns: INTERFACE_LINES,
			maxHints: 60,
		});
		const leads: Lead[] = scan.hints.map((hint) => ({
			at: { change: hint.file, line: hint.line },
			kind: hint.pattern,
			facts: factsOf(hint.flags),
		}));
		const count = (label: string) => countLabel(scan, label);
		const facts = {
			interfaceLinesAdded: scan.matched,
			symbolImages: count("symbol-image"),
			assetImages: count("asset-image"),
			accessibilityModifiers: count("accessibility-modifier"),
			fixedSizeCustomFonts: count("fixed-size-custom-font"),
			systemFontsWithPointSize: count("system-font-with-a-point-size"),
			scaledCustomFonts: count("scaled-custom-font"),
			semanticFonts: count("semantic-font"),
			heightCaps: count("height-cap"),
			colourByState: count("colour-by-state"),
			filesWithoutCheckout: scan.filesWithoutCheckout,
			filesScanned: scan.filesScanned,
			linesAdded: scan.linesAdded,
		};
		const directions: string[] = [...sampleNote(scan)];
		if (facts.symbolImages + facts.assetImages > 0) {
			directions.push(
				`${String(facts.symbolImages + facts.assetImages)} image(s) added against ${String(facts.accessibilityModifiers)} accessibility modifier(s) — for each image inside a Button, Toggle or tap gesture, read the whole view chain for a label or a Label title.`,
			);
		}
		if (facts.fixedSizeCustomFonts > 0) {
			directions.push(
				`${String(facts.fixedSizeCustomFonts)} custom font(s) with fixedSize: added — these do not scale with the user's text size.`,
			);
		}
		if (facts.systemFontsWithPointSize > 0) {
			directions.push(
				`${String(facts.systemFontsWithPointSize)} system font(s) with a point size added — fixed unless the size itself scales (a @ScaledMetric value); read where each size comes from. A custom font given size: scales with the body style and is not fixed.`,
			);
		}
		if (facts.heightCaps > 0) {
			directions.push(
				`${String(facts.heightCaps)} height cap(s) added — a cap clips only when the container holds text.`,
			);
		}
		if (leads.length === 0) {
			directions.push(...noMatchDirections(scan.linesAdded, scan.filesScanned));
		}
		return { leads, facts, directions };
	},
});
