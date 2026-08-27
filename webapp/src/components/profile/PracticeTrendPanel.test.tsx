import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { PracticeTrend } from "@/api/types.gen";
import { PracticeTrendPanel } from "./PracticeTrendPanel";

const trend = (overrides: Partial<PracticeTrend> = {}): PracticeTrend => ({
	slug: "maintainable-code",
	scope: "GROUP",
	direction: "IMPROVING",
	support: {
		currentOpportunities: 4,
		previousOpportunities: 4,
		opportunitiesUntilComparable: 0,
		comparablePractices: 3,
		eligiblePractices: 5,
		calendarSpanDays: 9,
		bundleSize: 4,
		ropeHalfWidth: 0.15,
		credibilityThreshold: 0.9,
	},
	previousOutcomes: {
		demonstratedStrengths: 1,
		safeAvoidances: 1,
		commissionProblems: 2,
		omissionGaps: 3,
		notApplicable: 0,
	},
	currentOutcomes: {
		demonstratedStrengths: 4,
		safeAvoidances: 2,
		commissionProblems: 1,
		omissionGaps: 1,
		notApplicable: 0,
	},
	opportunities: [
		{
			index: 0,
			occurredAt: new Date("2026-08-01T09:00:00Z"),
			workKind: "PULL_REQUEST",
			reviewedWorkId: 1,
			bundle: "PREVIOUS",
			outcomes: {
				demonstratedStrengths: 1,
				safeAvoidances: 0,
				commissionProblems: 1,
				omissionGaps: 0,
				notApplicable: 0,
			},
		},
		{
			index: 1,
			occurredAt: new Date("2026-08-09T09:00:00Z"),
			workKind: "PULL_REQUEST",
			reviewedWorkId: 2,
			bundle: "CURRENT",
			outcomes: {
				demonstratedStrengths: 1,
				safeAvoidances: 1,
				commissionProblems: 0,
				omissionGaps: 0,
				notApplicable: 0,
			},
		},
	],
	...overrides,
});

describe("PracticeTrendPanel", () => {
	it("renders an accessible comparison and visible provenance", () => {
		render(<PracticeTrendPanel trend={trend()} />);

		screen.getByRole("heading", { name: "Recent direction" });
		screen.getByText("More positive recently");
		screen.getByRole("img", {
			name: /Latest 4 reviews: 6 strengths, 2 to work on.*Earlier 4 reviews: 2 strengths, 5 to work on/,
		});
		screen.getByText(
			"Compared your latest 4 reviewed work items with the 4 before them, spanning 9 days.",
		);
		screen.getByText("3 of 5 practices in this group had comparable evidence.");
		screen.getByText("This describes recent evidence, not your overall ability.");
	});

	it("shows an actionable empty state without comparison bars", () => {
		render(
			<PracticeTrendPanel
				trend={trend({
					direction: "INSUFFICIENT_EVIDENCE",
					currentOutcomes: undefined,
					previousOutcomes: undefined,
					opportunities: [],
					support: {
						...trend().support,
						currentOpportunities: 0,
						previousOpportunities: 0,
						opportunitiesUntilComparable: 3,
						calendarSpanDays: undefined,
					},
				})}
			/>,
		);

		screen.getByText("Not enough to compare yet");
		screen.getByText("3 more reviewed work items will make a comparison possible.");
		expect(screen.queryByText("Earlier")).toBeNull();
		screen.getByText("No reviewed work items are available yet.");
	});

	it("keeps the opportunity strip out of the tab order and names it for screen readers", () => {
		const { container } = render(<PracticeTrendPanel trend={trend()} />);

		screen.getByRole("img", { name: /evidence opportunities, ordered oldest to newest/ });
		expect(container.querySelectorAll('[tabindex="0"]')).toHaveLength(0);
	});

	it("explains an uncertain full comparison without inventing a score", () => {
		render(<PracticeTrendPanel trend={trend({ direction: "UNCERTAIN" })} />);
		screen.getByText("Direction unclear");
		screen.getByText("The available evidence does not support one clear recent direction yet.");
		expect(screen.queryByText(/score/i)).toBeNull();
	});
});
