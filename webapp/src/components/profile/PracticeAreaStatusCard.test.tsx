import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { PracticeArea, PracticeAreaStatus, ReflectionPractice } from "@/api/types.gen";
import { PracticeAreaStatusCard } from "./PracticeAreaStatusCard";

const makeArea = (id: number, slug: string, name: string, description?: string): PracticeArea => ({
	id,
	slug,
	name,
	description,
	displayOrder: id,
	visibleInPracticeDashboards: true,
	autonomy: { effective: "AUTOMATIC", inherited: true, source: "WORKSPACE" },
	createdAt: new Date("2026-01-01T00:00:00Z"),
	updatedAt: new Date("2026-01-01T00:00:00Z"),
});

const makePractice = (
	slug: string,
	standing: ReflectionPractice["standing"],
): ReflectionPractice => ({
	slug,
	name: slug,
	standing,
	strengths: [],
	toWorkOn: [],
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

const trendSupport = {
	level: "WELL_SUPPORTED" as const,
	currentOpportunities: 4,
	previousOpportunities: 4,
	opportunitiesUntilComparable: 0,
	bundleSize: 4,
	ropeHalfWidth: 0.15,
	credibilityThreshold: 0.9,
};

const statuses: Record<string, PracticeAreaStatus | undefined> = {
	"code-quality": {
		areaSlug: "code-quality",
		areaName: "Code Quality",
		status: "DEVELOPING",
		guidance:
			"Your recent feedback points to “Include tests with the change” as the next practice to focus on.",
		guidanceSource: "RULE_BASED",
		trajectory: "DECLINING",
		trendSupport,
		feedbackSpanDays: 12,
		feedbackSince: new Date("2026-07-15T09:00:00Z"),
		items: [
			{
				observationId: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
				title: "Missing rollout plan",
				deliveredFeedback: "Add a rollout section describing how the change ships.",
				severity: "MAJOR",
				artifactKind: "PULL_REQUEST",
				artifactId: 42,
				origin: "LIVE",
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
		trendSupport,
		feedbackSpanDays: 1,
		feedbackSince: new Date("2026-07-27T08:00:00Z"),
		items: [
			{
				observationId: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c002",
				title: "Responsive to review feedback",
				artifactKind: "PULL_REQUEST",
				artifactId: 43,
				origin: "LIVE",
			},
		],
		sources: [{ source: "PULL_REQUEST", count: 1 }],
	},
	"review-communication": {
		areaSlug: "review-communication",
		areaName: "Review Communication",
		status: "NOT_OBSERVED",
		items: [],
		sources: [],
	},
};

describe("PracticeAreaStatusCard", () => {
	it("shows a skeleton while loading", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={{}} isLoading={true} />);

		screen.getByTestId("practice-area-status-loading");
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

		screen.getByText("Could not load your practice-area status");
	});

	it("puts the area needing the most work first", () => {
		render(
			<PracticeAreaStatusCard
				areas={areas}
				statuses={statuses}
				isLoading={false}
				practicesByArea={{
					// Configured order is code-quality, collaboration, review-communication; the grid must
					// reorder so the area with the most practices needing attention leads.
					collaboration: [
						makePractice("respond", "DEVELOPING"),
						makePractice("threads", "DEVELOPING"),
					],
					"code-quality": [makePractice("naming", "DEVELOPING")],
				}}
			/>,
		);

		const titles = Array.from(document.querySelectorAll('[data-slot="card-title"]')).map(
			(node) => node.textContent,
		);
		expect(titles).toEqual(["Collaboration", "Code Quality", "Review Communication"]);
	});

	it("collapses beyond three areas and toggles the rest into view", () => {
		const manyAreas = [...areas, makeArea(4, "delivery", "Delivery"), makeArea(5, "docs", "Docs")];
		render(<PracticeAreaStatusCard areas={manyAreas} statuses={statuses} isLoading={false} />);

		const cardTitles = () =>
			Array.from(document.querySelectorAll('[data-slot="card-title"]')).map(
				(node) => node.textContent,
			);
		expect(cardTitles()).toHaveLength(3);
		expect(cardTitles()).not.toContain("Docs");

		const toggle = screen.getByRole("button", { name: "Show all 5 practice areas" });
		expect(toggle.getAttribute("aria-expanded")).toBe("false");
		fireEvent.click(toggle);

		expect(cardTitles()).toHaveLength(5);
		expect(cardTitles()).toContain("Docs");
		const collapse = screen.getByRole("button", { name: "Show fewer areas" });
		expect(collapse.getAttribute("aria-expanded")).toBe("true");
		fireEvent.click(collapse);
		expect(cardTitles()).toHaveLength(3);
	});

	it("shows every area without a toggle while they still fit", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		expect(document.querySelectorAll('[data-slot="card-title"]')).toHaveLength(3);
		expect(screen.queryByRole("button", { name: /Show all/ })).toBeNull();
	});

	it("renders one card per area with the call-to-action status label", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		screen.getByText("Code Quality");
		screen.getByText("Needs attention");
		screen.getByText("Collaboration");
		screen.getByText("Going well");
		screen.getByText("Review Communication");
		screen.getByText("Not observed yet");
	});

	it("keeps per-observation text off the overview entirely", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		// Neither the guidance prose nor a single finding's headline belongs on a card meant to be
		// scanned across a grid — both are introduced on the detail surface instead.
		expect(
			screen.queryByText(
				"Your recent feedback points to “Include tests with the change” as the next practice to focus on.",
			),
		).toBeNull();
		expect(screen.queryByText("Missing rollout plan")).toBeNull();
	});

	it("says so plainly when an area has nothing to act on", () => {
		render(<PracticeAreaStatusCard areas={[areas[1]]} statuses={statuses} isLoading={false} />);

		screen.getByText("Nothing needs your attention here right now.");
	});

	it("keeps the item's full feedback markdown off the overview card", () => {
		render(<PracticeAreaStatusCard areas={[areas[0]]} statuses={statuses} isLoading={false} />);

		expect(screen.queryByText("Feedback highlight")).toBeNull();
		expect(screen.queryByText("Add a rollout section describing how the change ships.")).toBeNull();
	});

	it("shows how the area's practices split, and says what that means", () => {
		render(
			<PracticeAreaStatusCard
				areas={[areas[0]]}
				statuses={statuses}
				isLoading={false}
				practicesByArea={{
					"code-quality": [
						makePractice("small-functions", "DEVELOPING"),
						makePractice("naming", "MIXED"),
						makePractice("comments", "STRENGTH"),
						makePractice("dead-code", "STRENGTH"),
					],
				}}
			/>,
		);

		screen.getByText("Needs your attention");
		screen.getByText("Across 4 practices: 1 needs attention, 1 mixed, 2 going well");
		screen.getByTestId("practice-area-standing-ring");
		expect(screen.queryByRole("button", { name: /Needs your attention:/ })).toBeNull();
	});

	it("draws the practices that carry no measurement yet as their own share", () => {
		render(
			<PracticeAreaStatusCard
				areas={[areas[0]]}
				statuses={statuses}
				isLoading={false}
				practicesByArea={{ "code-quality": [makePractice("naming", "STRENGTH")] }}
				// The area holds four practices; the reflection only knows one, so three are unmeasured.
				practiceCountByArea={{ "code-quality": 4 }}
			/>,
		);

		screen.getByText("Across 4 practices: 1 going well, 3 not assessed yet");
		screen.getByTestId("practice-area-standing-ring");
	});

	it("congratulates an area whose practices are all going well", () => {
		render(
			<PracticeAreaStatusCard
				areas={[areas[1]]}
				statuses={statuses}
				isLoading={false}
				practicesByArea={{
					collaboration: [makePractice("respond-to-comments", "STRENGTH")],
				}}
			/>,
		);

		// Scoped to the card's verdict line: the section legend names the same standing, so an
		// unqualified text query now matches twice.
		screen.getByText("Going well", { selector: "p" });
		screen.getByTestId("practice-area-standing-ring");
	});

	it("renders no bar until the per-practice standings arrive", () => {
		render(<PracticeAreaStatusCard areas={[areas[0]]} statuses={statuses} isLoading={false} />);

		expect(screen.queryByRole("img")).toBeNull();
	});

	it("keeps the card frame neutral — the ring is the colour signal", () => {
		const { container } = render(
			<PracticeAreaStatusCard areas={[areas[0]]} statuses={statuses} isLoading={false} />,
		);

		expect(container.querySelector(".ring-destructive\\/40")).toBeNull();
	});

	it("makes the whole card the way in", () => {
		const onOpenDetails = vi.fn();
		render(
			<PracticeAreaStatusCard
				areas={[areas[0]]}
				statuses={statuses}
				isLoading={false}
				onOpenDetails={onOpenDetails}
			/>,
		);

		// The overlay spans the card, so a click anywhere but on a nested control opens the area.
		const overlay = screen.getByRole("button", { name: "See details about Code Quality" });
		expect(overlay.className).toContain("absolute");
		fireEvent.click(overlay);

		expect(onOpenDetails).toHaveBeenCalledWith(areas[0]);
	});

	it("shows provenance chips for the artifacts the feedback comes from", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		screen.getByText("Based on 6 sources");
		screen.getByText("Based on 1 pull request");
		screen.getByRole("button", { name: "3 pull requests" });
		screen.getByRole("button", { name: "1 issue" });
		screen.getByRole("button", { name: "2 Slack conversations" });
	});

	it("shows the common feedback window below the sources", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		screen.getByText("Feedback window: last 12 days");
		screen.getByText("Feedback window: today");
	});

	it("hints at the trajectory without turning it into a score", () => {
		render(
			<PracticeAreaStatusCard
				areas={areas}
				statuses={statuses}
				isLoading={false}
				practicesByArea={{
					"code-quality": [makePractice("naming", "DEVELOPING")],
					collaboration: [makePractice("respond", "STRENGTH")],
				}}
			/>,
		);

		screen.getByText("More positive recently");
		screen.getByText("More difficulties recently");
	});

	it("keeps stable and insufficient-evidence directions visible without a standing ring", () => {
		const codeQualityStatus = statuses["code-quality"];
		const reviewCommunicationStatus = statuses["review-communication"];
		if (!codeQualityStatus || !reviewCommunicationStatus) {
			throw new Error("Expected status fixtures are missing");
		}
		const supportWithoutComparison = {
			...trendSupport,
			level: "NONE" as const,
			currentOpportunities: 2,
			previousOpportunities: 0,
			opportunitiesUntilComparable: 2,
		};
		render(
			<PracticeAreaStatusCard
				areas={[areas[0], areas[2]]}
				statuses={{
					"code-quality": {
						...codeQualityStatus,
						trajectory: "STABLE",
					},
					"review-communication": {
						...reviewCommunicationStatus,
						trajectory: "INSUFFICIENT_EVIDENCE",
						trendSupport: supportWithoutComparison,
					},
				}}
				isLoading={false}
			/>,
		);

		screen.getByText("Broadly consistent lately");
		screen.getByText("Not enough to compare yet");
		expect(screen.queryByRole("img")).toBeNull();
	});

	it("keeps the area name as plain text instead of a hover-only action", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		screen.getByText("Code Quality");
		expect(screen.queryByRole("button", { name: "Code Quality" })).toBeNull();
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

	it("says WHY an area carries no verdict instead of one generic empty state", () => {
		render(<PracticeAreaStatusCard areas={[areas[2]]} statuses={statuses} isLoading={false} />);

		screen.getByText("These practices have not been observed in your reviewed work yet.");
	});

	it("distinguishes work that offered no opportunity from never having been observed", () => {
		render(
			<PracticeAreaStatusCard
				areas={[areas[2]]}
				statuses={{
					"review-communication": {
						areaSlug: "review-communication",
						areaName: "Review Communication",
						status: "NO_OPPORTUNITY",
						items: [],
						sources: [],
					},
				}}
				isLoading={false}
			/>,
		);

		screen.getByText("Nothing to report yet");
		screen.getByText(
			"Your recent work was reviewed, but it either offered no opportunity for these practices or raised nothing worth mentioning.",
		);
		// Reviewed-but-quiet is not the same silence as never-looked-at, and must not borrow its wording.
		expect(screen.queryByText("Not observed yet")).toBeNull();
	});

	it("treats an area whose status has not arrived yet as not-observed", () => {
		render(<PracticeAreaStatusCard areas={[areas[0]]} statuses={{}} isLoading={false} />);

		screen.getByText("Not observed yet");
	});

	it("explains when the workspace has no practice areas", () => {
		render(<PracticeAreaStatusCard areas={[]} statuses={{}} isLoading={false} />);

		screen.getByText("No practice areas are configured in this workspace yet.");
	});

	it("decodes the ring's colours in a legend, so the grid explains its own visual", () => {
		render(
			<PracticeAreaStatusCard
				areas={areas}
				statuses={statuses}
				isLoading={false}
				practicesByArea={{ "code-quality": [makePractice("naming", "DEVELOPING")] }}
			/>,
		);

		const legend = within(screen.getByRole("list", { name: "What the ring colours mean" }));
		for (const label of ["Needs attention", "Mixed", "Going well", "Not assessed yet"]) {
			legend.getByText(label);
		}
	});

	it("leaves the legend out until a card actually draws a ring", () => {
		render(<PracticeAreaStatusCard areas={areas} statuses={statuses} isLoading={false} />);

		expect(screen.queryByRole("list", { name: "What the ring colours mean" })).toBeNull();
	});

	it("states the feedback window and the ordering once for the whole grid", () => {
		render(
			<PracticeAreaStatusCard
				areas={areas}
				statuses={statuses}
				isLoading={false}
				practicesByArea={{ "code-quality": [makePractice("naming", "DEVELOPING")] }}
			/>,
		);

		// The widest window across the grid, not one card's — 12 days here against collaboration's 1.
		screen.getByText("Based on the last 12 days · Sorted by where to start");
	});

	it("names only the integrations the feedback actually came from", () => {
		render(
			<PracticeAreaStatusCard
				areas={[areas[1]]}
				statuses={statuses}
				isLoading={false}
				practicesByArea={{ collaboration: [makePractice("respond-to-comments", "STRENGTH")] }}
			/>,
		);

		// Collaboration rests on pull requests alone, so no issue or Slack noun may appear.
		screen.getByText(
			"Each area groups a few concrete practices — habits we can see in your pull requests.",
		);
	});
});
