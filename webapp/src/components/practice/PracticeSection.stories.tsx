import type { Meta, StoryObj } from "@storybook/react";
import { RefreshCw, Search, XCircleIcon } from "lucide-react";
import type { FindingFeedbackEngagement } from "@/api/types.gen";
import { EmptyState } from "@/components/shared/EmptyState";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { mockFindings, mockSummaries } from "./__fixtures__/mock-data";
import { EngagementOverview } from "./EngagementOverview";
import { FindingsList } from "./FindingsList";
import { PracticeSummaryGrid } from "./PracticeSummaryGrid";

/**
 * PracticeSection is a container that calls usePracticeFindings internally.
 * Stories use a presentation wrapper with mock data to avoid MSW/provider
 * setup. The wrapper mirrors the real component's layout and states to
 * validate visual correctness. Integration testing of the hook + component
 * wiring is covered by E2E tests.
 */

interface PracticeSectionStoryProps {
	summaries: typeof mockSummaries;
	findings: typeof mockFindings;
	engagement?: FindingFeedbackEngagement;
	isLoading?: boolean;
	isEmpty?: boolean;
	isError?: boolean;
}

function PracticeSectionStory({
	summaries,
	findings,
	engagement,
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

	const totalFindings = summaries.reduce((sum, s) => sum + s.totalFindings, 0);

	return (
		<div className="flex flex-col gap-6">
			<h2 className="text-xl font-semibold">Practices</h2>
			{engagement && totalFindings > 0 && (
				<EngagementOverview engagement={engagement} totalFindings={totalFindings} />
			)}
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
		engagement: { applied: 8, disputed: 2, notApplicable: 1 },
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
