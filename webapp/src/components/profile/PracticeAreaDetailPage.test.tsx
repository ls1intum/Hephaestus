import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type {
	ObservationDetail,
	ObservationList,
	PracticeArea,
	PracticeAreaStatus,
} from "@/api/types.gen";
import { PracticeAreaDetailPage } from "./PracticeAreaDetailPage";

const area: PracticeArea = {
	id: 1,
	active: true,
	slug: "review-ready-work",
	name: "Packaging work for review",
	description: "Make a change cheap to review before you ask for one.",
	displayOrder: 1,
	createdAt: new Date("2026-01-01T00:00:00Z"),
	updatedAt: new Date("2026-01-01T00:00:00Z"),
};

const status: PracticeAreaStatus = {
	areaSlug: area.slug,
	areaName: area.name,
	status: "DEVELOPING",
	sources: [{ source: "PULL_REQUEST", count: 2 }],
	guidance:
		"Your recent feedback points to “Scope the change to one concern” as the next practice to focus on.",
	guidanceSource: "RULE_BASED",
	trajectory: "REGRESSING",
	feedbackSpanDays: 8,
	items: [],
};

const practices = [
	{
		slug: "scope-one-concern",
		name: "Scope the change to one concern",
		whyItMatters: "Small, single-purpose changes are reviewed faster and more thoroughly.",
		whatGoodLooksLike: "One self-contained concern per change, split when it grows.",
	},
	{ slug: "describe-what-changed", name: "Describe what changed and why" },
];

const makeObservation = (overrides: Partial<ObservationList>): ObservationList => ({
	id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
	practiceSlug: "scope-one-concern",
	practiceName: "Scope the change to one concern",
	artifactType: "PULL_REQUEST",
	artifactId: 41,
	title: "Change bundles two unrelated concerns",
	presence: "ABSENT",
	assessment: "BAD",
	severity: "MINOR",
	confidence: 0.9,
	observedAt: new Date("2026-08-03T09:00:00Z"),
	...overrides,
});

const activity: ObservationList[] = [
	makeObservation({}),
	makeObservation({
		id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c002",
		title: "Constructive thread reply",
		presence: "PRESENT",
		assessment: "GOOD",
		severity: undefined,
		artifactType: "CONVERSATION_THREAD",
		artifactId: 7,
	}),
];

describe("PracticeAreaDetailPage", () => {
	it("shows a retryable error when related activity fails", () => {
		const onRetryActivity = vi.fn();
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activityError={new Error("boom")}
				onRetryActivity={onRetryActivity}
				isLoading={false}
			/>,
		);

		expect(screen.getByText("Could not load related activity")).toBeTruthy();
		fireEvent.click(screen.getByRole("button", { name: "Retry" }));
		expect(onRetryActivity).toHaveBeenCalledOnce();
	});

	it("renders the derived status header", () => {
		render(<PracticeAreaDetailPage area={area} status={status} isLoading={false} />);

		expect(screen.getByRole("heading", { name: area.name })).toBeTruthy();
		expect(screen.getAllByText("Needs attention").length).toBeGreaterThan(0);
		expect(screen.getByText("More to work on lately")).toBeTruthy();
		expect(
			screen.getByText(
				"Your recent feedback points to “Scope the change to one concern” as the next practice to focus on.",
			),
		).toBeTruthy();
	});

	it("draws the assessment flow: practice nodes feeding the area node", () => {
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				practices={practices}
				practiceStandings={{ "scope-one-concern": "DEVELOPING" }}
				isLoading={false}
			/>,
		);

		expect(screen.getByText("How this area is assessed")).toBeTruthy();
		// The diagram renders a desktop and a stacked mobile variant; both exist in the DOM.
		expect(
			screen.getAllByRole("button", { name: /Scope the change to one concern.*Needs attention/ })
				.length,
		).toBeGreaterThan(0);
		// A practice without feedback says so instead of pretending a verdict.
		expect(
			screen.getAllByRole("button", { name: /Describe what changed and why.*No feedback yet/ })
				.length,
		).toBeGreaterThan(0);
		expect(
			screen.getByText(
				"Feedback on these practices in your work determines where you stand in this area.",
			),
		).toBeTruthy();
	});

	it("selects a practice node, shows its framing inline, and clears again", () => {
		const onSelectPractice = vi.fn();
		const { rerender } = render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				practices={practices}
				onSelectPractice={onSelectPractice}
				isLoading={false}
			/>,
		);

		fireEvent.click(screen.getAllByRole("button", { name: /Scope the change to one concern/ })[0]);
		expect(onSelectPractice).toHaveBeenCalledWith("scope-one-concern");

		rerender(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				practices={practices}
				practiceTrajectories={{ "scope-one-concern": "IMPROVING" }}
				selectedPracticeSlug="scope-one-concern"
				onSelectPractice={onSelectPractice}
				isLoading={false}
			/>,
		);
		// The sheet's educational content now lives on the main screen.
		expect(screen.getByText("Development")).toBeTruthy();
		expect(
			screen.getByText(
				"Your latest day with feedback showed more strengths or fewer areas to work on than the previous day.",
			),
		).toBeTruthy();
		expect(screen.getByText("Why it matters")).toBeTruthy();
		expect(
			screen.getByText("Small, single-purpose changes are reviewed faster and more thoroughly."),
		).toBeTruthy();
		expect(screen.getByText("What good looks like")).toBeTruthy();

		// Clicking the selected node again clears the selection.
		fireEvent.click(screen.getAllByRole("button", { name: /Scope the change to one concern/ })[0]);
		expect(onSelectPractice).toHaveBeenLastCalledWith(undefined);
	});

	it("shows which integration each activity entry comes from", () => {
		render(
			<PracticeAreaDetailPage area={area} status={status} activity={activity} isLoading={false} />,
		);

		expect(screen.getByText(/Pull request on GitHub/)).toBeTruthy();
		expect(screen.getByText(/Conversation on Slack/)).toBeTruthy();
	});

	it("offers integration filters in the configure popover", () => {
		const onActivityFiltersChange = vi.fn();
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={activity}
				activityFilters={{ sources: [], severities: [] }}
				onActivityFiltersChange={onActivityFiltersChange}
				isLoading={false}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: /^Filter/ }));
		fireEvent.click(screen.getByRole("checkbox", { name: "Conversations (Slack)" }));
		expect(onActivityFiltersChange).toHaveBeenCalledWith({
			sources: ["CONVERSATION_THREAD"],
			severities: [],
		});

		fireEvent.click(screen.getByRole("checkbox", { name: "Major" }));
		expect(onActivityFiltersChange).toHaveBeenLastCalledWith({
			sources: [],
			severities: ["MAJOR"],
		});
	});

	it("expands guidance and reasoning inline instead of opening another view", () => {
		const onToggleObservation = vi.fn();
		const detail: ObservationDetail = {
			id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
			practiceSlug: "scope-one-concern",
			practiceName: "Scope the change to one concern",
			artifactType: "PULL_REQUEST",
			artifactId: 41,
			title: "Change bundles two unrelated concerns",
			presence: "ABSENT",
			assessment: "BAD",
			severity: "MINOR",
			confidence: 0.9,
			reasoning: "The diff mixes a refactoring with a behaviour change across 14 files.",
			guidance: "Split the refactoring from the feature change so each can be reviewed alone.",
			observedAt: new Date("2026-08-03T09:00:00Z"),
		};
		const { rerender } = render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={activity}
				onToggleObservation={onToggleObservation}
				isLoading={false}
			/>,
		);

		fireEvent.click(screen.getAllByRole("button", { name: /Show details/ })[0]);
		expect(onToggleObservation).toHaveBeenCalledWith("0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001");

		rerender(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={activity}
				openObservationId="0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001"
				observationDetail={{ isLoading: false, detail }}
				onToggleObservation={onToggleObservation}
				isLoading={false}
			/>,
		);
		expect(screen.getByText("What to do")).toBeTruthy();
		expect(
			screen.getByText(
				"Split the refactoring from the feature change so each can be reviewed alone.",
			),
		).toBeTruthy();
		expect(screen.getByText("Why this finding was raised")).toBeTruthy();
		expect(
			screen.getByText("The diff mixes a refactoring with a behaviour change across 14 files."),
		).toBeTruthy();

		fireEvent.click(screen.getByRole("button", { name: /Hide details/ }));
		expect(onToggleObservation).toHaveBeenLastCalledWith("0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001");
	});

	it("shows a loading state for the feed instead of a premature empty state", () => {
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={[]}
				isActivityLoading
				isLoading={false}
			/>,
		);

		expect(screen.getByTestId("related-activity-loading")).toBeTruthy();
		expect(screen.queryByText("No related activity")).toBeNull();
	});

	it("loads more activity on demand", () => {
		const onLoadMoreActivity = vi.fn();
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={activity}
				hasMoreActivity
				onLoadMoreActivity={onLoadMoreActivity}
				isLoading={false}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "View more related activity" }));
		expect(onLoadMoreActivity).toHaveBeenCalledOnce();
	});

	it("explains an empty feed differently when filters are active", () => {
		const { rerender } = render(
			<PracticeAreaDetailPage area={area} status={status} activity={[]} isLoading={false} />,
		);
		expect(screen.getByText("Events appear here once your work has been reviewed.")).toBeTruthy();

		rerender(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={[]}
				activityFilters={{ sources: ["CONVERSATION_THREAD"], severities: [] }}
				isLoading={false}
			/>,
		);
		expect(
			screen.getByText(
				"No events match the current filters. Clear them to see everything in this area.",
			),
		).toBeTruthy();
	});

	it("switches sorting via the chevron chips", () => {
		const onActivitySortChange = vi.fn();
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={activity}
				activitySort={{ by: "DATE", direction: "DESC" }}
				onActivitySortChange={onActivitySortChange}
				isLoading={false}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: /Severity/ }));
		expect(onActivitySortChange).toHaveBeenCalledWith({ by: "SEVERITY", direction: "DESC" });

		// Clicking the date chip toggles its direction.
		fireEvent.click(screen.getByRole("button", { name: /Newest/ }));
		expect(onActivitySortChange).toHaveBeenLastCalledWith({ by: "DATE", direction: "ASC" });
	});

	it("toggles the severity direction on a second click", () => {
		const onActivitySortChange = vi.fn();
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={activity}
				activitySort={{ by: "SEVERITY", direction: "DESC" }}
				onActivitySortChange={onActivitySortChange}
				isLoading={false}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: /Severity/ }));
		expect(onActivitySortChange).toHaveBeenCalledWith({ by: "SEVERITY", direction: "ASC" });
	});

	it("links to the reviewed artifact when the expanded observation resolves one", () => {
		const detail: ObservationDetail = {
			id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
			practiceSlug: "scope-one-concern",
			practiceName: "Scope the change to one concern",
			artifactType: "PULL_REQUEST",
			artifactId: 41,
			title: "Change bundles two unrelated concerns",
			presence: "ABSENT",
			assessment: "BAD",
			severity: "MINOR",
			confidence: 0.9,
			guidance: "Split the refactoring from the feature change.",
			artifactUrl: "https://github.com/acme/repo/pull/521",
			observedAt: new Date("2026-08-03T09:00:00Z"),
		};
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={activity}
				openObservationId={detail.id}
				observationDetail={{ isLoading: false, detail }}
				isLoading={false}
			/>,
		);

		const link = screen.getByRole("link", { name: /Open pull request on GitHub/ });
		expect(link.getAttribute("href")).toBe("https://github.com/acme/repo/pull/521");
	});

	it("collapses a long area description behind a show-more toggle", () => {
		const longArea = {
			...area,
			description:
				"Make a change cheap to review before you ask for one. Keep it to a single self-contained concern of a readable size, say what changed and why, write commit subjects a reviewer can follow, and link it to the issue it answers.",
		};
		render(<PracticeAreaDetailPage area={longArea} status={status} isLoading={false} />);

		fireEvent.click(screen.getByRole("button", { name: "Show more" }));
		expect(screen.getByRole("button", { name: "Show less" })).toBeTruthy();
	});

	it("swaps the legend for the practice panel when a practice is clicked", () => {
		const onSelectPractice = vi.fn();
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				practices={practices}
				onSelectPractice={onSelectPractice}
				isLoading={false}
			/>,
		);

		fireEvent.click(screen.getByRole("button", { name: "How to read this diagram" }));
		expect(screen.getByText("How to read this diagram", { selector: "h3" })).toBeTruthy();

		// Clicking a practice must replace the legend, not stack beneath it.
		fireEvent.click(screen.getAllByRole("button", { name: /Scope the change to one concern/ })[0]);
		expect(onSelectPractice).toHaveBeenCalledWith("scope-one-concern");
		expect(screen.queryByText("How to read this diagram", { selector: "h3" })).toBeNull();
	});

	it("navigates back when the optional action is connected", () => {
		const onBack = vi.fn();
		render(
			<PracticeAreaDetailPage area={area} status={status} isLoading={false} onBack={onBack} />,
		);

		fireEvent.click(screen.getByRole("button", { name: "Back to profile" }));

		expect(onBack).toHaveBeenCalledOnce();
	});

	it("explains an unknown or inactive area instead of crashing, with a way back", () => {
		const onBack = vi.fn();
		render(<PracticeAreaDetailPage area={undefined} isLoading={false} onBack={onBack} />);

		expect(
			screen.getByText("This practice area does not exist or is not active in this workspace."),
		).toBeTruthy();

		fireEvent.click(screen.getByRole("button", { name: "Back to profile" }));
		expect(onBack).toHaveBeenCalledOnce();
	});

	it("shows page loading and error states", () => {
		const { rerender } = render(<PracticeAreaDetailPage area={area} isLoading={true} />);
		expect(screen.getByTestId("practice-area-detail-loading")).toBeTruthy();

		rerender(<PracticeAreaDetailPage area={area} isLoading={false} error={new Error("boom")} />);
		expect(screen.getByText(`Could not load your status for ${area.name}`)).toBeTruthy();
	});
});
