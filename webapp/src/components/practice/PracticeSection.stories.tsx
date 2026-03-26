import type { Meta, StoryObj } from "@storybook/react";
import { subDays, subHours } from "date-fns";
import { RefreshCw, Search, XCircleIcon } from "lucide-react";
import type { ContributorPracticeSummary, PracticeFindingList } from "@/api/types.gen";
import { EmptyState } from "@/components/shared/EmptyState";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FindingsList } from "./FindingsList";
import { PracticeSummaryGrid } from "./PracticeSummaryGrid";

const mockSummaries: ContributorPracticeSummary[] = [
	{
		practiceName: "Code Review Thoroughness",
		practiceSlug: "code-review-thoroughness",
		category: "Code Quality",
		positiveCount: 15,
		negativeCount: 3,
		totalFindings: 18,
		lastFindingAt: new Date(),
	},
	{
		practiceName: "Test Coverage",
		practiceSlug: "test-coverage",
		category: "Testing",
		positiveCount: 5,
		negativeCount: 8,
		totalFindings: 13,
		lastFindingAt: new Date(),
	},
	{
		practiceName: "Error Handling",
		practiceSlug: "error-handling",
		category: "Reliability",
		positiveCount: 7,
		negativeCount: 4,
		totalFindings: 11,
		lastFindingAt: new Date(),
	},
	{
		practiceName: "Documentation Standards",
		practiceSlug: "documentation-standards",
		category: "Documentation",
		positiveCount: 10,
		negativeCount: 2,
		totalFindings: 12,
		lastFindingAt: new Date(),
	},
];

const mockFindings: PracticeFindingList[] = [
	{
		id: "f1",
		title: "Reviewer provided thorough inline feedback on error handling paths",
		verdict: "POSITIVE",
		severity: "MINOR",
		confidence: 0.92,
		detectedAt: subHours(new Date(), 3),
		practiceName: "Code Review Thoroughness",
		practiceSlug: "code-review-thoroughness",
		category: "Code Quality",
		targetId: 42,
		targetType: "pull_request",
	},
	{
		id: "f2",
		title: "Missing error handling in async database operation",
		verdict: "NEGATIVE",
		severity: "MAJOR",
		confidence: 0.88,
		detectedAt: subHours(new Date(), 8),
		practiceName: "Error Handling",
		practiceSlug: "error-handling",
		category: "Reliability",
		targetId: 43,
		targetType: "pull_request",
	},
	{
		id: "f3",
		title: "Comprehensive test suite added for authentication module",
		verdict: "POSITIVE",
		severity: "INFO",
		confidence: 0.95,
		detectedAt: subDays(new Date(), 1),
		practiceName: "Test Coverage",
		practiceSlug: "test-coverage",
		category: "Testing",
		targetId: 44,
		targetType: "pull_request",
	},
	{
		id: "f4",
		title: "Review comment addressed edge case in payment processing",
		verdict: "POSITIVE",
		severity: "MINOR",
		confidence: 0.85,
		detectedAt: subDays(new Date(), 2),
		practiceName: "Code Review Thoroughness",
		practiceSlug: "code-review-thoroughness",
		category: "Code Quality",
		targetId: 45,
		targetType: "review",
	},
	{
		id: "f5",
		title: "Unhandled promise rejection in event listener cleanup",
		verdict: "NEGATIVE",
		severity: "CRITICAL",
		confidence: 0.91,
		detectedAt: subDays(new Date(), 3),
		practiceName: "Error Handling",
		practiceSlug: "error-handling",
		category: "Reliability",
		targetId: 46,
		targetType: "pull_request",
	},
];

/**
 * PracticeSection is a container that calls usePracticeFindings internally.
 * Stories use a presentation wrapper with mock data to avoid MSW/provider
 * setup. The wrapper mirrors the real component's layout and states to
 * validate visual correctness. Integration testing of the hook + component
 * wiring is covered by E2E tests.
 */

interface PracticeSectionStoryProps {
	summaries: ContributorPracticeSummary[];
	findings: PracticeFindingList[];
	isLoading?: boolean;
	isEmpty?: boolean;
	isError?: boolean;
}

function PracticeSectionStory({
	summaries,
	findings,
	isLoading = false,
	isEmpty = false,
	isError = false,
}: PracticeSectionStoryProps) {
	if (isError) {
		return (
			<div className="flex flex-col gap-3">
				<h2 className="text-xl font-semibold">Practices</h2>
				<Alert variant="destructive" className="max-w-xl">
					<XCircleIcon className="h-4 w-4" />
					<AlertTitle>Failed to load practice data</AlertTitle>
					<AlertDescription>Something went wrong loading your practice findings.</AlertDescription>
				</Alert>
				<Button variant="outline" size="sm" className="self-start" onClick={() => {}}>
					<RefreshCw className="mr-2 h-4 w-4" />
					Retry
				</Button>
			</div>
		);
	}

	if (isEmpty) {
		return (
			<div className="flex flex-col gap-3">
				<h2 className="text-xl font-semibold">Practices</h2>
				<EmptyState
					icon={Search}
					title="No practice findings yet"
					description="Findings from automated practice detection will appear here as you contribute."
				/>
			</div>
		);
	}

	const practiceOptions = summaries.map((s) => ({
		value: s.practiceSlug,
		label: s.practiceName,
	}));

	return (
		<div className="flex flex-col gap-6">
			<h2 className="text-xl font-semibold">Practices</h2>
			<PracticeSummaryGrid
				summaries={summaries}
				selectedPracticeSlug={null}
				onPracticeSelect={() => {}}
				isLoading={isLoading}
			/>
			<FindingsList
				findings={findings}
				practiceOptions={practiceOptions}
				selectedPracticeSlug={null}
				selectedVerdict="ALL"
				onPracticeSelect={() => {}}
				onVerdictChange={() => {}}
				hasMore={!isLoading && findings.length > 0}
				isLoading={isLoading}
			/>
		</div>
	);
}

const meta = {
	title: "Components/Practice/PracticeSection",
	component: PracticeSectionStory,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Full practices section composing summary grid and findings list. This story renders the layout directly with mock data since the real component uses hooks.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		isLoading: { control: "boolean", description: "Loading state" },
		isEmpty: { control: "boolean", description: "Empty state (no findings)" },
		isError: { control: "boolean", description: "Error state" },
	},
} satisfies Meta<typeof PracticeSectionStory>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Full section with summary cards and findings list.
 */
export const WithFindings: Story = {
	args: {
		summaries: mockSummaries,
		findings: mockFindings,
	},
};

/**
 * Empty state when no practice findings exist.
 */
export const Empty: Story = {
	args: {
		summaries: [],
		findings: [],
		isEmpty: true,
	},
};

/**
 * Loading skeleton state.
 */
export const Loading: Story = {
	args: {
		summaries: [],
		findings: [],
		isLoading: true,
	},
};

/**
 * Error state with retry button.
 */
export const ErrorState: Story = {
	args: {
		summaries: [],
		findings: [],
		isError: true,
	},
};
