import { describe, expect, it } from "vitest";

import { feedbackDisplayMarkdown } from "./feedback-display";
import { feedbackOpeningLine } from "./feedback-preview";

describe("feedbackOpeningLine", () => {
	it("returns a plain sentence unchanged", () => {
		expect(feedbackOpeningLine("You kept the controller focused.")).toBe(
			"You kept the controller focused.",
		);
	});

	it("keeps the whole first line with no ellipsis, since the sheet shows the rest", () => {
		expect(feedbackOpeningLine("2 issues to tighten.\n\n- The lookup collapses two failures")).toBe(
			"2 issues to tighten.",
		);
	});

	it("unwraps headings, emphasis and inline code", () => {
		expect(feedbackOpeningLine("## What worked\n\nThe controller stays.")).toBe("What worked");
		expect(feedbackOpeningLine("The **controller** stays `focused` on HTTP.")).toBe(
			"The controller stays focused on HTTP.",
		);
	});

	it("keeps a link's words and drops its target", () => {
		expect(
			feedbackOpeningLine(
				"See [the naming guideline](https://example.com/guide) for the longer version.",
			),
		).toBe("See the naming guideline for the longer version.");
	});

	it("leaves out a fenced code block, and the line that introduced it", () => {
		expect(
			feedbackOpeningLine("You wrote:\n\n```java\nreturn null;\n```\n\nPrefer an Optional."),
		).toBe("Prefer an Optional.");
	});

	it("keeps a lead-in that introduces prose rather than a block", () => {
		expect(feedbackOpeningLine("Two options:\n\nRename it, or keep both for a release.")).toBe(
			"Two options:",
		);
	});

	// The colon stays: it is what says the omitted thing was going to follow.
	it("keeps the lead-in when the fence it introduced was all the body had", () => {
		expect(feedbackOpeningLine("The lookup reads:\n\n```java\nreturn repository.findVisi")).toBe(
			"The lookup reads:",
		);
		expect(feedbackOpeningLine("Quoted:\n\n```md\n<sub>a footer</sub>\n```\n")).toBe("Quoted:");
	});

	it("drops a rule and a list bullet", () => {
		expect(feedbackOpeningLine("---\n\nSecond point.")).toBe("Second point.");
		expect(feedbackOpeningLine("- rename the field\n- keep both for a release")).toBe(
			"rename the field",
		);
	});

	it.each(["\r\n", "\r"])("removes the transport marker with %j line endings", (ending) => {
		const body = `${MARKER}${ending}Keep it focused.${ending}`;
		expect(feedbackDisplayMarkdown(body)).toBe("Keep it focused.\n");
		expect(feedbackOpeningLine(body)).toBe("Keep it focused.");
	});

	it("skips a code quote and hidden markers to the first line of prose", () => {
		expect(
			feedbackOpeningLine(`${MARKER}\n\`\`\`java\nreturn null;\n\`\`\`\n**Return** an empty list.`),
		).toBe("Return an empty list.");
	});

	it("reports no text when the feedback carries no body", () => {
		expect(feedbackOpeningLine(undefined)).toBeUndefined();
		expect(feedbackOpeningLine("```java\nreturn null;\n```")).toBeUndefined();
	});
});

const MARKER = "<!-- hephaestus:practice-review:0b7d3c2e-8f14-4a6b-9c5d-2e1f0a3b4c5d -->";
const DISCLOSURE =
	"<sub>Practice review &middot; Model. This feedback is AI-generated and can be inaccurate. Answer or disagree in [Hephaestus](https://example.com/respond).</sub>";
const SETTINGS = "<sub>[Why you see this and how to stop it](https://example.com/settings)</sub>";

describe("feedbackDisplayMarkdown", () => {
	it("drops the marker and unwraps the footer of a posted body, keeping its words and links", () => {
		expect(
			feedbackDisplayMarkdown(`${MARKER}\nKeep it focused.\n\n---\n${DISCLOSURE}\n${SETTINGS}\n`),
		).toBe(
			"Keep it focused.\n\n---\nPractice review &middot; Model. This feedback is AI-generated and can be inaccurate. Answer or disagree in [Hephaestus](https://example.com/respond).\n[Why you see this and how to stop it](https://example.com/settings)\n",
		);
	});

	it("returns a body with neither as the same string", () => {
		const body = "Keep it focused.\r\n\r\n  - one   \n\n<sub>authored aside</sub>\n";
		expect(feedbackDisplayMarkdown(body)).toBe(body);
	});

	it("preserves unmodified text and a quoted marker with CRLF line endings", () => {
		const plain = "Keep it focused.\r\n\r\n  - one   \r\n";
		const quoted = `\`\`\`md\r\n${MARKER}\r\n\`\`\`\r\n`;
		expect(feedbackDisplayMarkdown(plain)).toBe(plain);
		expect(feedbackDisplayMarkdown(quoted)).toBe(quoted);
	});

	it.each([
		["a backtick fence", `\`\`\`md\n${MARKER}\n${DISCLOSURE}\n\`\`\`\n`],
		["a tilde fence", `~~~\n${MARKER}\n${DISCLOSURE}\n~~~\n`],
		["indented code", `Quoted:\n\n    ${MARKER}\n\n    ${DISCLOSURE}\n`],
		["inline code", `Write \`${MARKER}\` or \`${DISCLOSURE}\`.\n`],
		["other HTML", `<details>\n\n${DISCLOSURE} said by the author\n\n</details>\n`],
	])("leaves both alone when they are quoted in %s", (_case, body) => {
		expect(feedbackDisplayMarkdown(body)).toBe(body);
	});
});
