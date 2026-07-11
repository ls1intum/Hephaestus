import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { expect, fn, screen } from "storybook/test";
import { getDeveloperPracticeReportOptions } from "@/api/@tanstack/react-query.gen";
import type { PracticeReportCard, PracticeReportSummary } from "@/api/types.gen";
import { developerPracticeReportError } from "@/mocks/handlers";
import { DeveloperDrillDownDialog } from "./DeveloperDrillDownDialog";

const WORKSPACE = "demo";
const LOGIN = "zoe";
const USER_ID = 42;

const developer: PracticeReportSummary = {
	userId: USER_ID,
	userLogin: LOGIN,
	name: "Zoe Attention",
	avatarUrl: "",
	needsAttention: true,
	attentionReasons: ["Clear PR description: gaps to work on this cycle"],
	standings: [{ name: "Clear PR description", slug: "clear-pr", standing: "DEVELOPING" }],
};

const cards: PracticeReportCard[] = [
	{
		name: "Clear PR description",
		slug: "clear-pr",
		standing: "DEVELOPING",
		whyItMatters: "Reviewers need the intent, not just the diff.",
		strengths: [],
		toWorkOn: [
			{
				observationId: "o1",
				title: "Describe the why in the PR body",
				artifactType: "PULL_REQUEST",
				artifactId: 42,
				guidance: "Add a short summary of the change and its motivation.",
			},
		],
	},
];

// Seeds the drill-down report into the cache so the query resolves synchronously. Unseeded queries
// fall back to a never-resolving fn → the loading state.
function seededClient(report?: PracticeReportCard[]): QueryClient {
	const client = new QueryClient({
		defaultOptions: { queries: { retry: false, queryFn: () => new Promise<never>(() => {}) } },
	});
	if (report) {
		client.setQueryData(
			getDeveloperPracticeReportOptions({ path: { workspaceSlug: WORKSPACE, userId: USER_ID } })
				.queryKey,
			report,
		);
	}
	return client;
}

/** Read-only mentor drill-down of one developer's reflection. Fetching it writes the transparency audit row. */
const meta = {
	component: DeveloperDrillDownDialog,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: { workspaceSlug: WORKSPACE, developer, onClose: fn() },
} satisfies Meta<typeof DeveloperDrillDownDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The developer's practice cards, resolved from cache. */
export const Loaded: Story = {
	decorators: [
		(Story) => (
			<QueryClientProvider client={seededClient(cards)}>
				<Story />
			</QueryClientProvider>
		),
	],
	play: async () => {
		await screen.findByRole("dialog");
		await expect(screen.getByRole("heading", { name: cards[0].name })).toBeInTheDocument();
	},
};

/** The developer has no feedback this cycle — the neutral empty state. */
export const Empty: Story = {
	decorators: [
		(Story) => (
			<QueryClientProvider client={seededClient([])}>
				<Story />
			</QueryClientProvider>
		),
	],
	play: async () => {
		await screen.findByRole("dialog");
		await expect(screen.getByText("No recent practice feedback")).toBeInTheDocument();
	},
};

/** Still resolving the report — the loading skeleton. */
export const Loading: Story = {
	decorators: [
		(Story) => (
			<QueryClientProvider client={seededClient()}>
				<Story />
			</QueryClientProvider>
		),
	],
	play: async () => {
		await screen.findByRole("dialog");
		await expect(screen.queryByRole("heading", { name: cards[0].name })).not.toBeInTheDocument();
		await expect(screen.queryByText("No recent practice feedback")).not.toBeInTheDocument();
	},
};

/** The report request failed — the error empty-state with a retry action. The generated query hook
 * always ships its own `queryFn` (calling the real API client), which takes precedence over any
 * `queryFn` set on a local `QueryClient`'s `defaultOptions` — so the failure has to come from the
 * network layer itself, via an MSW handler that 500s the request, rather than from the cache. */
export const ErrorState: Story = {
	parameters: { msw: { handlers: [developerPracticeReportError] } },
	play: async () => {
		await screen.findByRole("dialog");
		// The dialog itself renders immediately; the error state only appears once the mocked
		// request has actually failed, so wait for the text rather than asserting synchronously.
		await expect(
			await screen.findByText("Couldn't load this developer's feedback"),
		).toBeInTheDocument();
		await expect(screen.getByRole("button", { name: /retry/i })).toBeInTheDocument();
	},
};
