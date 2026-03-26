import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { FeedbackBadge } from "./FeedbackBadge";

const queryClient = new QueryClient({
	defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY } },
});

/**
 * Read-only badge showing the latest feedback status for a finding.
 * Feedback is created automatically when users respond to finding guidance
 * (e.g., implementing a suggestion marks it as "Applied").
 */
const meta = {
	component: FeedbackBadge,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Read-only badge showing feedback status. Feedback is recorded automatically when users act on findings.",
			},
		},
	},
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<QueryClientProvider client={queryClient}>
				<div className="max-w-md">
					<Story />
				</div>
			</QueryClientProvider>
		),
	],
} satisfies Meta<typeof FeedbackBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state — fetches latest feedback for the given finding.
 */
export const Default: Story = {
	args: {
		workspaceSlug: "demo-workspace",
		findingId: "f1a2b3c4-d5e6-7890-abcd-ef1234567890",
	},
};
