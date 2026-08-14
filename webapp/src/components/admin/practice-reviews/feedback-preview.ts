import type { ReviewFeedback } from "@/api/types.gen";

/**
 * The opening words of a piece of feedback, as a line of prose a row can be told apart by.
 *
 * <h4>What the server sends</h4>
 * `bodyPreview` is `left(body, 320)` over the stored body, and the stored body is the Markdown note
 * the developer receives: a lead line, bold finding headings with an inline-code file locator, a
 * fenced quote of their code, an italic aside, and `---` between findings. A real note runs one to
 * three thousand characters, so 320 of them lands somewhere inside the first fenced block. Printing
 * that verbatim gives a row title of `**🔴 A cache miss …** · \`ReviewQueryService.java:118\` You
 * wrote: ```java return repository.findVisible(workspaceId, id)` — markup, a language tag and half a
 * statement, in the one place on the screen that is supposed to say what the feedback is about.
 *
 * <h4>What this does</h4>
 * Flattens that to the sentence a person would read out. Code fences go entirely — a fragment of
 * somebody's Java is never the useful summary of a note about their Java — as do rules and list
 * bullets, and the inline markers are unwrapped rather than deleted so the words survive.
 *
 * <h4>The ellipsis is not decoration</h4>
 * It marks the two different cuts this text has been through: the server's 320 characters, and this
 * function dropping a block it would not print. Without it a preview that stops mid-clause reads as
 * feedback that was itself truncated, which is the reading the product owner arrived at. `bodyTruncated`
 * has been on the wire since the endpoint shipped and nothing had ever read it.
 *
 * Returns `undefined` when there is no body, which is a real state — conversation feedback is
 * recorded before anything is composed — and the caller says so in its own words.
 */
export function feedbackPreviewText(
	feedback: Pick<ReviewFeedback, "bodyPreview" | "bodyTruncated">,
): string | undefined {
	const source = feedback.bodyPreview;
	if (!source) return undefined;

	const { text, dropped } = flattenMarkdown(source);
	if (!text) return undefined;
	// A full stop immediately before the ellipsis reads as a typo rather than as a continuation.
	return feedback.bodyTruncated || dropped ? `${text.replace(/\.$/, "")}…` : text;
}

interface Flattened {
	text: string;
	/** Whether anything was left out, as opposed to merely being unwrapped. */
	dropped: boolean;
}

function flattenMarkdown(source: string): Flattened {
	const lines = source.split("\n");
	const kept: string[] = [];
	let dropped = false;
	let insideFence = false;

	for (const line of lines) {
		if (line.trimStart().startsWith("```")) {
			// An opening fence starts a block we skip; a closing one ends it. A preview cut mid-block
			// never sees the closing fence, which is why `insideFence` also ends the loop's output.
			insideFence = !insideFence;
			dropped = true;
			continue;
		}
		if (insideFence) continue;
		const trimmed = line.trim();
		if (!trimmed) continue;
		// A horizontal rule separates two findings; in one line of prose it is a false sentence break.
		if (/^([-*_])\1{2,}$/.test(trimmed.replace(/\s/g, ""))) {
			dropped = true;
			continue;
		}
		kept.push(inlineToText(trimmed.replace(/^#{1,6}\s+/, "").replace(/^([-*+]|\d+\.|>)\s+/, "")));
	}

	return { text: kept.join(" ").replace(/\s+/g, " ").trim(), dropped };
}

/**
 * Inline markers unwrapped to the words they were marking.
 *
 * Link text is kept and the target dropped: a URL in a two-line preview is the least readable thing
 * that could occupy it, and the row already links somewhere of its own.
 */
function inlineToText(line: string): string {
	return line
		.replace(/!\[[^\]]*\]\([^)]*\)/g, "")
		.replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
		.replace(/`+/g, "")
		.replace(/(\*\*|__)(.+?)\1/g, "$2")
		.replace(/(?<![\w*])[*_](?=\S)(.+?)(?<=\S)[*_](?![\w*])/g, "$1");
}
