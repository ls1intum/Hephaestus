import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { FeedbackButtons } from "./FeedbackButtons";

const queryClient = new QueryClient({
	defaultOptions: { queries: { retry: false, staleTime: Number.POSITIVE_INFINITY } },
});

const meta = {
	component: FeedbackButtons,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Feedback buttons for practice findings. Users can mark findings as Applied, Disputed, or N/A.",
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
} satisfies Meta<typeof FeedbackButtons>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state with no existing feedback.
 */
export const Default: Story = {
	args: {
		workspaceSlug: "demo-workspace",
		findingId: "f1a2b3c4-d5e6-7890-abcd-ef1234567890",
	},
};
