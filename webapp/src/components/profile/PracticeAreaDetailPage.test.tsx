import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type {
	ObservationDetail,
	ObservationList,
	PracticeArea,
	PracticeAreaStatus,
	PracticeTrend,
} from "@/api/types.gen";
import { PracticeAreaDetailPage } from "./PracticeAreaDetailPage";

const area: PracticeArea = {
	id: 1,
	slug: "review-ready-work",
	name: "Packaging work for review",
	description: "Make a change cheap to review before you ask for one.",
	displayOrder: 1,
	visibleInPracticeDashboards: true,
	autonomy: { effective: "AUTOMATIC", inherited: true, source: "WORKSPACE" },
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
	trajectory: "DECLINING",
	trendSupport: {
		level: "WELL_SUPPORTED",
		currentOpportunities: 4,
		previousOpportunities: 4,
		opportunitiesUntilComparable: 0,
		bundleSize: 4,
		ropeHalfWidth: 0.15,
		credibilityThreshold: 0.9,
	},
	feedbackSpanDays: 8,
	items: [],
};

const makeTrend = (direction: PracticeTrend["direction"] = "DECLINING"): PracticeTrend => ({
	slug: area.slug,
	scope: "AREA",
	direction,
	support: {
		level: "WELL_SUPPORTED",
		currentOpportunities: 4,
		previousOpportunities: 4,
		opportunitiesUntilComparable: 0,
		calendarSpanDays: 8,
		bundleSize: 4,
		ropeHalfWidth: 0.15,
		credibilityThreshold: 0.9,
	},
	currentOutcomes: {
		demonstratedStrengths: 1,
		safeAvoidances: 0,
		commissionProblems: 2,
		omissionGaps: 1,
		notApplicable: 0,
	},
	previousOutcomes: {
		demonstratedStrengths: 3,
		safeAvoidances: 1,
		commissionProblems: 0,
		omissionGaps: 1,
		notApplicable: 0,
	},
	opportunities: [],
});

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
	artifactKind: "PULL_REQUEST",
	artifactId: 41,
	summary: "Change bundles two unrelated concerns",
	presence: "ABSENT",
	assessment: "BAD",
	severity: "MINOR",
	claimCurrentness: "CURRENT",
	origin: "LIVE",
	observedAt: new Date("2026-08-03T09:00:00Z"),
	...overrides,
});

const activity: ObservationList[] = [
	makeObservation({}),
	makeObservation({
		id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c002",
		summary: "Constructive thread reply",
		presence: "PRESENT",
		assessment: "GOOD",
		severity: undefined,
		artifactKind: "CONVERSATION_THREAD",
		artifactId: 7,
	}),
];

describe("PracticeAreaDetailPage", () => {
	it("shows a retryable error when feedback history fails", () => {
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

		screen.getByText("Could not load feedback history");
		fireEvent.click(screen.getByRole("button", { name: "Retry" }));
		expect(onRetryActivity).toHaveBeenCalledOnce();
	});

	it("renders the derived status header", () => {
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				areaTrend={makeTrend()}
				isLoading={false}
			/>,
		);

		screen.getByRole("heading", { name: area.name });
		expect(screen.getAllByText("Needs attention").length).toBeGreaterThan(0);
		screen.getByText("More difficulties recently");
		screen.getByRole("button", { name: "About this area" });
		expect(screen.queryByText("How this was determined")).toBeNull();
		screen.getByText(
			"Your recent feedback points to “Scope the change to one concern” as the next practice to focus on.",
		);
	});

	it("lists the practices and their current standings", () => {
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				practices={practices}
				practiceStandings={{ "scope-one-concern": "DEVELOPING" }}
				isLoading={false}
			/>,
		);

		screen.getByText("Practices in this area");
		screen.getByRole("button", { name: "Show feedback for Scope the change to one concern" });
		// A practice without feedback says so instead of pretending a verdict.
		screen.getByRole("button", { name: "Show feedback for Describe what changed and why" });
		screen.getByText("Not measured yet");
		screen.getByText(
			"Select a practice to filter the history. Use its info button for more context.",
		);
	});

	it("selects a practice as an explicit feedback filter and clears it again", () => {
		const onSelectPractice = vi.fn();
		const { rerender } = render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				practices={practices}
				practiceNextSteps={{
					"scope-one-concern":
						"Split the refactoring from the feature change so each can be reviewed alone.",
				}}
				onSelectPractice={onSelectPractice}
				isLoading={false}
			/>,
		);

		fireEvent.click(
			screen.getByRole("button", {
				name: "Show feedback for Scope the change to one concern",
			}),
		);
		expect(onSelectPractice).toHaveBeenCalledWith("scope-one-concern");

		rerender(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				practices={practices}
				practiceTrends={{
					"scope-one-concern": {
						...makeTrend("STABLE"),
						scope: "PRACTICE",
						slug: "scope-one-concern",
					},
				}}
				practiceNextSteps={{
					"scope-one-concern":
						"Split the refactoring from the feature change so each can be reviewed alone.",
				}}
				selectedPracticeSlug="scope-one-concern"
				onSelectPractice={onSelectPractice}
				isLoading={false}
			/>,
		);
		expect(screen.queryByText("Showing feedback")).toBeNull();
		screen.getByText("Broadly consistent lately");
		screen.getByRole("button", { name: "Showing: Scope the change to one concern" });
		screen.getByText("Your next step");
		screen.getByText(
			"Split the refactoring from the feature change so each can be reviewed alone.",
		);
		expect(screen.queryByText("Why it matters")).toBeNull();

		// Clicking the selected practice again clears the selection.
		fireEvent.click(
			screen.getByRole("button", {
				name: "Clear feedback filter for Scope the change to one concern",
				pressed: true,
			}),
		);
		expect(onSelectPractice).toHaveBeenLastCalledWith(undefined);
	});

	it("opens rarely needed practice context directly below that practice", () => {
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

		const aboutPractice = screen.getByRole("button", {
			name: "About Scope the change to one concern",
		});
		expect(aboutPractice.getAttribute("aria-expanded")).toBe("false");
		fireEvent.click(aboutPractice);

		expect(aboutPractice.getAttribute("aria-expanded")).toBe("true");
		screen.getByText("Why it matters");
		screen.getByText("Small, single-purpose changes are reviewed faster and more thoroughly.");
		screen.getByText("What good looks like");
		expect(onSelectPractice).not.toHaveBeenCalled();
	});

	it("shows which integration each activity entry comes from", () => {
		render(
			<PracticeAreaDetailPage area={area} status={status} activity={activity} isLoading={false} />,
		);

		screen.getByText(/Pull request on GitHub/);
		screen.getByText(/Conversation on Slack/);
		screen.getByText("Expected practice missing");
		screen.getByText("Strength shown");
	});

	it("groups findings from the same artifact review into one timeline moment", () => {
		const onToggleObservation = vi.fn();
		const sameReviewActivity = [
			makeObservation({}),
			makeObservation({
				id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c003",
				practiceSlug: "describe-what-changed",
				practiceName: "Describe what changed and why",
				presence: "PRESENT",
				assessment: "GOOD",
				severity: undefined,
			}),
		];
		render(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={sameReviewActivity}
				onToggleObservation={onToggleObservation}
				isLoading={false}
			/>,
		);

		expect(screen.getAllByText("Pull request on GitHub")).toHaveLength(1);
		screen.getByText("Describe what changed and why");

		const findingControl = screen.getByRole("button", {
			name: /Scope the change to one concern.*Expected practice missing/i,
		});
		fireEvent.click(findingControl);
		expect(onToggleObservation).toHaveBeenCalledWith("0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001");
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

		// The filter is worded by the action a band asks for, not as a claimed "Major impact".
		fireEvent.click(screen.getByRole("checkbox", { name: "Fix before merge" }));
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
			artifactKind: "PULL_REQUEST",
			artifactId: 41,
			summary: "Change bundles two unrelated concerns",
			presence: "ABSENT",
			assessment: "BAD",
			severity: "MINOR",
			claimCurrentness: "CURRENT",
			origin: "LIVE",
			evidence: {
				citations: [
					{
						sourceKind: "scm.pull-request.diff",
						artifactPath: "acme/repo#521",
						path: "server/src/main/java/example/PracticeCatalogLoader.java",
						side: "NEW",
						startLine: 48,
						endLine: 52,
						quote: [
							"var workspaceCatalog = catalogByWorkspace.get(workspaceSlug);",
							"if (workspaceCatalog == null) {",
							"  return Catalog.empty();",
							"}",
							"return workspaceCatalog.load();",
						].join("\n"),
						quoteRedacted: false,
					},
				],
			},
			evidenceRationale: "The diff mixes a refactoring with a behaviour change across 14 files.",
			deliveredFeedback:
				"Split the refactoring from the feature change so each can be reviewed alone.",
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

		fireEvent.click(
			screen.getByRole("button", {
				name: /Scope the change to one concern.*Expected practice missing/i,
			}),
		);
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
		screen.getByText("What to try next");
		screen.getByText(
			"Split the refactoring from the feature change so each can be reviewed alone.",
		);
		screen.getByText("Why this was noted");
		screen.getByText("The diff mixes a refactoring with a behaviour change across 14 files.");
		// The evidence renders as a file block: the name always visible, the directory able to truncate,
		// and the line range beside it rather than concatenated into the path.
		screen.getByText("PracticeCatalogLoader.java");
		screen.getByText("server/src/main/java/example/");
		screen.getByText("48–52");
		// The quote is bound to that file and carries a line number per line.
		screen.getByText("var workspaceCatalog = catalogByWorkspace.get(workspaceSlug);");
		screen.getByText("return workspaceCatalog.load();");
		screen.getByText("48");
		screen.getByText("49");

		fireEvent.click(
			screen.getByRole("button", {
				name: /Scope the change to one concern.*Expected practice missing/i,
			}),
		);
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

		screen.getByTestId("related-activity-loading");
		expect(screen.queryByText("No feedback history")).toBeNull();
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

		fireEvent.click(screen.getByRole("button", { name: "View earlier reviews" }));
		expect(onLoadMoreActivity).toHaveBeenCalledOnce();
	});

	it("explains an empty feed differently when filters are active", () => {
		const { rerender } = render(
			<PracticeAreaDetailPage area={area} status={status} activity={[]} isLoading={false} />,
		);
		screen.getByText("Feedback appears here once your work has been reviewed.");

		rerender(
			<PracticeAreaDetailPage
				area={area}
				status={status}
				activity={[]}
				activityFilters={{ sources: ["CONVERSATION_THREAD"], severities: [] }}
				isLoading={false}
			/>,
		);
		screen.getByText(
			"No feedback matches the current filters. Clear them to see everything in this area.",
		);
	});

	it("links to the reviewed artifact when the expanded observation resolves one", () => {
		const detail: ObservationDetail = {
			id: "0b54c9f2-8f4e-4a53-9be1-0e6a35a1c001",
			practiceSlug: "scope-one-concern",
			practiceName: "Scope the change to one concern",
			artifactKind: "PULL_REQUEST",
			artifactId: 41,
			summary: "Change bundles two unrelated concerns",
			presence: "ABSENT",
			assessment: "BAD",
			severity: "MINOR",
			claimCurrentness: "CURRENT",
			origin: "LIVE",
			deliveredFeedback: "Split the refactoring from the feature change.",
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

		const link = screen.getByRole("link", { name: /Pull request on GitHub/ });
		expect(link.getAttribute("href")).toBe("https://github.com/acme/repo/pull/521");
	});

	it("keeps the area description collapsed until it is requested", () => {
		const longArea = {
			...area,
			description:
				"Make a change cheap to review before you ask for one. Keep it to a single self-contained concern of a readable size, say what changed and why, write commit subjects a reviewer can follow, and link it to the issue it answers.",
		};
		render(<PracticeAreaDetailPage area={longArea} status={status} isLoading={false} />);

		const about = screen.getByRole("button", { name: "About this area" });
		expect(about.getAttribute("aria-expanded")).toBe("false");
		expect(screen.queryByText(longArea.description)).toBeNull();

		fireEvent.click(about);
		screen.getByText(longArea.description);
		expect(about.getAttribute("aria-expanded")).toBe("true");
	});

	it("does not reserve an empty detail panel before a practice is selected", () => {
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

		expect(screen.queryByText("Selected practice")).toBeNull();
		fireEvent.click(
			screen.getByRole("button", {
				name: "Show feedback for Scope the change to one concern",
			}),
		);
		expect(onSelectPractice).toHaveBeenCalledWith("scope-one-concern");
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

		screen.getByText("This practice area does not exist or is not active in this workspace.");

		fireEvent.click(screen.getByRole("button", { name: "Back to profile" }));
		expect(onBack).toHaveBeenCalledOnce();
	});

	it("shows page loading and error states", () => {
		const { rerender } = render(<PracticeAreaDetailPage area={area} isLoading={true} />);
		screen.getByTestId("practice-area-detail-loading");

		rerender(<PracticeAreaDetailPage area={area} isLoading={false} error={new Error("boom")} />);
		screen.getByText(`Could not load your status for ${area.name}`);
	});
});
