import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { PracticeArea, PracticeAreaStatus } from "@/api/types.gen";
import { PracticeAreaStatusCard } from "./PracticeAreaStatusCard";

const makeArea = (id: number, slug: string, name: string, description?: string): PracticeArea => ({
	id,
	active: true,
	slug,
	name,
	description,
	displayOrder: id,
	createdAt: new Date("2026-01-01T00:00:00Z"),
	updatedAt: new Date("2026-01-01T00:00:00Z"),
});

const areas: PracticeArea[] = [
	makeArea(
		1,
		"code-quality",
		"Code Quality",
		"Tracks how readable and maintainable your changes are.",
	),
	makeArea(2, "collaboration", "Collaboration"),
	makeArea(3, "review-communication", "Review Communication"),
];

const statuses: Record<string, PracticeAreaStatus | undefined> = {
	"code-quality": {
		areaSlug: "code-quality",
		areaName: "Code Quality",
		status: "DEVELOPING",
		guidance:
			"Your recent feedback points to “Include tests with the change” as the next practice to focus on.",
		guidanceSource: "RULE_BASED",
		trajectory: "REGRESSING",
		feedbackSpanDays: 12,
		feedbackSince: new Date("2026-07-15T09:00:00Z"),
		items: [
			{
				observationId: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
				title: "Missing rollout plan",
				guidance: "Add a rollout section describing how the change ships.",
				severity: "MAJOR",
				artifactType: "PULL_REQUEST",
				artifactId: 42,
			},
		],
		sources: [
			{ source: "PULL_REQUEST", count: 3 },
			{ source: "ISSUE", count: 1 },
			{ source: "CONVERSATION_THREAD", count: 2 },
		],
	},
	collaboration: {
		areaSlug: "collaboration",
		areaName: "Collaboration",
		status: "STRENGTH",
		guidance:
			"Your recent feedback shows a strength in “Respond to each review comment”. Keep building on it.",
		guidanceSource: "RULE_BASED",
		trajectory: "IMPROVING",
		feedbackSpanDays: 1,
		feedbackSince: new Date("2026-07-27T08:00:00Z"),
		items: [
			{
				observationId: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c002",
				title: "Responsive to review feedback",
				artifactType: "PULL_REQUEST",
				artifactId: 43,
			},
		],
		sources: [{ source: "PULL_REQUEST", count: 1 }],
	},
	"review-communication": {
		areaSlug: "review-communication",
		areaName: "Review Communication",
		status: "NO_DATA",
		items: [],
		sources: [],
	},
};

describe("PracticeAreaStatusCard", () => {
	it("shows a skeleton while loading", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={{}} isLoading={true} />);

		expect(screen.getByTestId("practice-area-status-loading")).toBeTruthy();
	});

	it("shows an error alert when a query failed", () => {
		render(
			<PracticeAreaStatusCard
				areas={areas}
				statuses={{}}
				isLoading={false}
				error={new Error("boom")}
				onRetry={vi.fn()}
			/>,
		);

		expect(screen.getByText("Could not load your practice-area status")).toBeTruthy();
	});

	it("renders one card per area with the call-to-action status label", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		expect(screen.getByText("Code Quality")).toBeTruthy();
		expect(screen.getByText("Needs attention")).toBeTruthy();
		expect(screen.getByText("Collaboration")).toBeTruthy();
		expect(screen.getByText("Going well")).toBeTruthy();
		expect(screen.getByText("Review Communication")).toBeTruthy();
		expect(screen.getByText("No feedback yet")).toBeTruthy();
	});

	it("shows the guidance text on a card that has feedback", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		expect(
			screen.getByText(
				"Your recent feedback points to “Include tests with the change” as the next practice to focus on.",
			),
		).toBeTruthy();
		expect(
			screen.getByText(
				"Your recent feedback shows a strength in “Respond to each review comment”. Keep building on it.",
			),
		).toBeTruthy();
	});

	it("does not surface individual feedback items on the overview card", () => {
		render(<PracticeAreaStatusCard areas={[areas[0]]} statuses={statuses} isLoading={false} />);

		expect(screen.queryByText("Feedback highlight")).toBeNull();
		expect(screen.queryByText("Missing rollout plan")).toBeNull();
	});

	it("tints the card frame with the status colour", () => {
		const { container } = render(
			<PracticeAreaStatusCard areas={[areas[0]]} statuses={statuses} isLoading={false} />,
		);

		expect(container.querySelector(".ring-destructive\\/40")).toBeTruthy();
	});

	it("shows provenance chips for the artifacts the feedback comes from", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		expect(screen.getByText("Feedback from 3 pull requests")).toBeTruthy();
		expect(screen.getByText("Feedback from 1 issue")).toBeTruthy();
		expect(screen.getByText("Feedback from 2 Slack conversations")).toBeTruthy();
		expect(screen.getByText("Feedback from 1 pull request")).toBeTruthy();
	});

	it("shows the actual feedback span, with a singular form for a single day", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		expect(screen.getByText("Based on feedback from the last 12 days")).toBeTruthy();
		expect(screen.getByText("Based on feedback from the last day")).toBeTruthy();
	});

	it("hints at the trajectory without turning it into a score", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		expect(screen.getByText("Improving lately")).toBeTruthy();
		expect(screen.getByText("More to work on lately")).toBeTruthy();
	});

	it("offers the area description behind the info affordance", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		expect(screen.getByRole("button", { name: "About Code Quality" })).toBeTruthy();
	});

	it("opens the deeper analysis for the selected area", () => {
		const onOpenDetails = vi.fn();
		render(
			<PracticeAreaStatusCard
				areas={areas}
				statuses={statuses}
				isLoading={false}
				onOpenDetails={onOpenDetails}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "See details about Code Quality" }));

		expect(onOpenDetails).toHaveBeenCalledWith(areas[0]);
	});

	it("does not render a dead detail action before a destination is connected", () => {
		render(<PracticeAreaStatusCard areas={[areas[0]]} statuses={statuses} isLoading={false} />);

		expect(screen.queryByRole("button", { name: "See details about Code Quality" })).toBeNull();
	});

	it("shows a clear no-data hint on an area without findings", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		expect(screen.getByText("Status appears once your work has been reviewed.")).toBeTruthy();
	});

	it("treats an area whose status has not arrived yet as no-data", () => {
		render(<PracticeAreaStatusCard areas={[areas[0]]} statuses={{}} isLoading={false} />);

		expect(screen.getByText("No feedback yet")).toBeTruthy();
	});

	it("explains when the workspace has no practice areas", () => {
		render(<PracticeAreaStatusCard areas={[]} statuses={{}} isLoading={false} />);

		expect(
			screen.getByText("No practice areas are configured in this workspace yet."),
		).toBeTruthy();
	});
});
