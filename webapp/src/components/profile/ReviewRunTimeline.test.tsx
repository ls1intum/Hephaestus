import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { PracticeGroupReviewObservation, PracticeGroupReviewRun } from "@/api/types.gen";
import { ReviewRunTimeline } from "./ReviewRunTimeline";

const baseObservation = {
	observationId: "00000000-0000-0000-0000-000000000102",
	feedbackId: "00000000-0000-0000-0000-000000000103",
	feedbackUsefulness: "HELPFUL",
	feedbackResolution: "ADDRESSED",
	feedbackResponseComment: "Applied in the next revision.",
	practiceSlug: "records-decisions",
	practiceName: "Record significant decisions and the reasoning",
	title: "The workspace trade-off is documented",
	presence: "PRESENT",
	assessment: "GOOD",
} satisfies PracticeGroupReviewObservation;

const run = {
	reviewId: "00000000-0000-0000-0000-000000000101",
	reviewedAt: new Date("2026-08-12T10:26:00Z"),
	reviewedWork: {
		type: "scm.pull_request",
		id: 902,
		provider: "GITHUB",
		number: 902,
		title: "Split the practice catalog loader per workspace",
		repositoryName: "HephaestusTest/practice-validation",
		url: "https://github.com/HephaestusTest/practice-validation/pull/902",
	},
	observations: [baseObservation],
} satisfies PracticeGroupReviewRun;

const runs = [run];

describe("ReviewRunTimeline", () => {
	it("renders the review-run boundary and its observations", () => {
		render(<ReviewRunTimeline runs={runs} />);

		screen.getByText("#902 · Split the practice catalog loader per workspace");
		screen.getByText("The workspace trade-off is documented");
		screen.getByText("Record significant decisions and the reasoning");
		screen.getByText("Strength shown");
	});

	it("passes the complete observation when usefulness changes", () => {
		const onChangeUsefulness = vi.fn();
		render(
			<ReviewRunTimeline
				runs={runs}
				onToggleObservation={() => undefined}
				openObservationId={baseObservation.observationId}
				onChangeUsefulness={onChangeUsefulness}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "Helpful" }));
		expect(onChangeUsefulness).toHaveBeenCalledWith(baseObservation, undefined);
	});

	it("keeps a dense review run compact until requested", () => {
		const denseRun: PracticeGroupReviewRun = {
			...run,
			observations: Array.from({ length: 5 }, (_, index) => ({
				...baseObservation,
				observationId: `00000000-0000-0000-0000-00000000010${index}`,
				practiceSlug: `practice-${index}`,
				practiceName: `Practice ${index + 1}`,
				title: `Observation ${index + 1}`,
			})),
		};

		render(<ReviewRunTimeline runs={[denseRun]} />);
		expect(screen.queryByText("Observation 4")).toBeNull();
		fireEvent.click(screen.getByRole("button", { name: "Show more (2)" }));
		screen.getByText("Observation 4");
		screen.getByText("Observation 5");
	});
});
