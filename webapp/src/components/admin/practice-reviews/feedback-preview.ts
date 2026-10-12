import { hasText } from "@/lib/text";

import { feedbackExcerptMarkdown } from "./feedback-display";

/**
 * The first line of prose of a whole piece of feedback, as the title of its sheet. No ellipsis: the
 * sheet shows the rest right under it, so the title is the opening line as written, not a cut.
 * `undefined` for the two states with no prose to show: no body at all, and a body that is nothing
 * but a code quote.
 */
export function feedbackOpeningLine(body: string | undefined): string | undefined {
	if (!hasText(body)) {
		return undefined;
	}
	return flattenMarkdown(feedbackExcerptMarkdown(body, false))[0];
}

/**
 * A Markdown note as its lines of prose, in order.
 *
 * Printed verbatim, a note's opening reads as markup, a language tag and half a statement. This
 * flattens it to the sentences a person would read out: code
 * fences go entirely — a fragment of somebody's Java is never the useful summary of a note about
 * their Java — as do rules and list bullets, while inline markers are unwrapped so the words survive.
 */
function flattenMarkdown(source: string): string[] {
	const lines = source.split("\n");
	const kept: string[] = [];
	let insideFence = false;
	/** The "You wrote:" line dropped with its block, held in case the block was all there was. */
	let leadIn: string | undefined;

	for (const line of lines) {
		if (line.trimStart().startsWith("```")) {
			// An opening fence starts a block we skip; a closing one ends it. A preview cut mid-block
			// never sees the closing fence, which is why `insideFence` also ends the loop's output.
			// The line introducing the block goes with it: "You wrote:" followed by the *next* paragraph
			// instead of the code claims the developer wrote something they did not.
			if (!insideFence && kept.at(-1)?.endsWith(":") === true) {
				leadIn = kept.pop();
			}
			insideFence = !insideFence;
			continue;
		}
		if (insideFence) {
			continue;
		}
		const trimmed = line.trim();
		if (!trimmed) {
			continue;
		}
		// A horizontal rule separates two results; in one line of prose it is a false sentence break.
		if (/^(?<rule>[-*_])\k<rule>{2,}$/u.test(trimmed.replaceAll(/\s/gu, ""))) {
			continue;
		}
		kept.push(
			inlineToText(trimmed.replace(/^#{1,6}\s+/u, "").replace(/^(?:[-*+]|\d+\.|>)\s+/u, "")),
		);
	}

	// Dropping the lead-in is right when prose follows the block and wrong when nothing does: where
	// the cut landed inside the first fence, that one line is every word of prose there is, and
	// popping it would report a note that has a body as having none.
	if (kept.length === 0 && hasText(leadIn)) {
		kept.push(leadIn);
	}

	return kept.map((line) => line.replaceAll(/\s+/gu, " ").trim()).filter(hasText);
}

/**
 * Link text is kept and the target dropped: a URL in a two-line preview is the least readable thing
 * that could occupy it, and the row already links somewhere of its own.
 */
function inlineToText(line: string): string {
	return line
		.replaceAll(/!\[[^\]]*\]\([^)]*\)/gu, "")
		.replaceAll(/\[(?<text>[^\]]+)\]\([^)]*\)/gu, "$<text>")
		.replaceAll(/`+/gu, "")
		.replaceAll(/(?<mark>\*\*|__)(?<text>.+?)\k<mark>/gu, "$<text>")
		.replaceAll(/(?<![\w*])[*_](?=\S)(?<text>.+?)(?<=\S)[*_](?![\w*])/gu, "$<text>");
}
