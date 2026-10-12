import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { EvidenceCitation, PracticeResultDetail } from "@/api/types.gen";
import { detailResult } from "@/stories/practice-detail-story-mock-data";

import { ReviewResultRow, type ReviewResultRowProps } from "./ReviewResultRow";

const result: PracticeResultDetail = {
	...detailResult,
	id: "00000000-0000-0000-0000-000000000001",
	feedbackResponse: { feedbackId: "00000000-0000-0000-0000-000000000002", usefulness: "HELPFUL" },
	practiceSlug: "explain-decisions",
	practiceName: "Explain significant decisions",
	summary: "The reason for the timeout is missing",
	outcome: "NOT_MET",
	severity: "MINOR",
	observations: [],
};

function renderOpen(props: Partial<ReviewResultRowProps> = {}) {
	return render(
		<ul>
			<ReviewResultRow result={result} onRespond={vi.fn()} {...props} />
		</ul>,
	);
}

const citation = (path: string): EvidenceCitation => ({
	sourceKind: "scm.pull-request.diff",
	artifactPath: "owner/repo#1",
	path,
	side: "NEW",
	startLine: 1,
	endLine: 1,
	quote: "return null;",
	quoteRedacted: false,
});

/** A quote of the pull request itself, which folds with every other quote of it in one list. */
const core = (quote: string): EvidenceCitation => ({
	sourceKind: "scm.pull-request.core",
	artifactPath: "owner/repo#1",
	path: "inputs/context/metadata.json",
	startLine: 1,
	endLine: 1,
	quote,
	quoteRedacted: false,
});

describe("ReviewResultRow", () => {
	it("keeps a diff pair that could not fold as two blocks, each with its own key", () => {
		// A key collision is only ever reported on the console, so the diagnostic is the assertion.
		using keyWarning = vi.spyOn(console, "error").mockReturnValue(undefined);
		renderOpen({
			result: {
				...result,
				evidence: {
					citations: [
						{ ...citation("src/config.ts"), side: "OLD", quote: "const timeout = 30;" },
						{
							...citation("src/config.ts"),
							side: "NEW",
							quote: undefined,
							quoteRedacted: true,
						},
					],
					undecidability: [],
				},
			},
		});

		expect(keyWarning).not.toHaveBeenCalled();
		expect(screen.getAllByText("config.ts")).toHaveLength(2);
		screen.getByText("const timeout = 30;");
		screen.getByText("Not quoted. The passage was withheld, so only its location was kept.");
	});

	it("quotes each problem's place under that problem, never folded into another's", () => {
		renderOpen({
			result: {
				...result,
				observations: [
					{
						observationId: "a",
						position: 0,
						severity: "MAJOR",
						summary: "The title says nothing",
						explanation: "It names the ticket only.",
						citations: [core('"title" : "PROJ-12"')],
					},
					{
						observationId: "b",
						position: 1,
						severity: "MINOR",
						summary: "The body is empty",
						explanation: "There is no description.",
						citations: [core('"body" : ""')],
					},
				],
			},
		});

		const problems = screen.getAllByRole("listitem").filter((item) => item.closest("ol") !== null);
		expect(problems).toHaveLength(2);
		expect(problems[0]?.textContent).toContain('"title" : "PROJ-12"');
		expect(problems[0]?.textContent).not.toContain('"body" : ""');
		expect(problems[1]?.textContent).toContain('"body" : ""');
		screen.getByText("Not met · 2 problems");
	});

	it("lists what could not be settled as open questions, never as a check", () => {
		renderOpen({
			result: {
				...result,
				outcome: "UNDETERMINED",
				evidence: {
					citations: [],
					undecidability: [
						{ openQuestion: "Was the move requested?", wouldSettleIt: "The linked issue." },
					],
				},
			},
		});

		screen.getByText("Open questions");
		screen.getByText("Was the move requested?");
		screen.getByText("The linked issue.");
		expect(screen.queryByText("What was checked")).toBeNull();
	});

	it("shows the authored next step without substituting the provider comment", () => {
		renderOpen({
			result: {
				...result,
				nextStep: "Split the commit so the rename can be reverted on its own.",
				deliveredFeedback:
					"<!-- hephaestus:summary -->\nProvider comment.\n<sub>AI-generated</sub>",
			},
		});

		screen.getByText("Split the commit so the rename can be reverted on its own.");
		expect(screen.queryByText(/hephaestus:summary/u)).toBeNull();
		expect(screen.queryByText(/Provider comment/u)).toBeNull();
	});

	it("keeps an unavailable next step absent while preserving the response", () => {
		renderOpen({
			result: {
				...result,
				nextStep: undefined,
				deliveredFeedback:
					"<!-- hephaestus:summary -->\nProvider comment.\n<sub>AI-generated</sub>",
			},
		});

		expect(screen.queryByText("Next step")).toBeNull();
		expect(screen.queryByText(/hephaestus:summary/u)).toBeNull();
		expect(screen.queryByText(/Provider comment/u)).toBeNull();
		screen.getByRole("button", { name: "Addressed" });
		screen.getByText("Helpful");
	});

	it("refuses a dispute made of blanks through the field rather than in silence", () => {
		const onRespond = vi.fn<NonNullable<ReviewResultRowProps["onRespond"]>>();
		renderOpen({ onRespond });

		fireEvent.click(screen.getByRole("button", { name: "Disputed" }));
		const comment = screen.getByRole<HTMLInputElement>("textbox", {
			name: "Why do you dispute this?",
		});
		fireEvent.change(comment, { target: { value: "   " } });
		fireEvent.click(screen.getByRole("button", { name: "Send" }));

		expect(comment.value).toBe("");
		expect(comment.validity.valueMissing).toBe(true);
		expect(onRespond).not.toHaveBeenCalled();
	});

	it("closes a dispute's band on Skip without recording it", () => {
		const onRespond = vi.fn<NonNullable<ReviewResultRowProps["onRespond"]>>();
		renderOpen({ onRespond });

		fireEvent.click(screen.getByRole("button", { name: "Disputed" }));
		fireEvent.click(screen.getByRole("button", { name: "Skip" }));

		expect(onRespond).not.toHaveBeenCalled();
		expect(screen.queryByRole("textbox")).toBeNull();
		expect(screen.getByRole("button", { name: "Disputed" }).getAttribute("aria-pressed")).toBe(
			"false",
		);
	});

	it("withdraws a recorded response, comment and all, when its button is pressed again", () => {
		const onRespond = vi.fn<NonNullable<ReviewResultRowProps["onRespond"]>>();
		renderOpen({
			result: {
				...result,
				feedbackResponse: {
					...result.feedbackResponse,
					feedbackId: "00000000-0000-0000-0000-000000000002",
					resolution: "ADDRESSED",
					comment: "Applied in the next revision.",
				},
			},
			onRespond,
		});

		// The response as it stands: the resolution pressed, the rating and the comment shown.
		expect(screen.getByRole("button", { name: "Addressed" }).getAttribute("aria-pressed")).toBe(
			"true",
		);
		screen.getByText("Helpful");
		screen.getByText("Applied in the next revision.");
		fireEvent.click(screen.getByRole("button", { name: "Addressed" }));

		expect(onRespond).toHaveBeenCalledExactlyOnceWith(
			{
				...result,
				feedbackResponse: {
					...result.feedbackResponse,
					feedbackId: "00000000-0000-0000-0000-000000000002",
					resolution: "ADDRESSED",
					comment: "Applied in the next revision.",
				},
			},
			{ comment: undefined, resolution: undefined, usefulness: "HELPFUL" },
		);
		expect(screen.queryByRole("textbox")).toBeNull();
	});
});
