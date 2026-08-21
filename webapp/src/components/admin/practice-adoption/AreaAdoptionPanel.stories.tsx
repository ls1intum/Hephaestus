import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import type { CatalogAreaAdoptionPreview } from "@/api/types.gen";
import { DetailDrawerStack } from "@/components/core/detail-drawer/DetailDrawerStack";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { expectGenuinelyDisabled } from "@/test/controls";
import { expectSettledVisible } from "@/test/overlay";
import { AreaAdoptionPanel } from "./AreaAdoptionPanel";

const practice = {
	slug: "describe-what-and-why",
	availability: "AVAILABLE" as const,
	etag: '"practice-preview"',
	initialAutonomy: "HUMAN_APPROVAL" as const,
	sourceReviewRuleFingerprint: mockAuthorDeclaredEvidenceValidation.reviewRuleFingerprint,
	area: { slug: "review-ready-work", disposition: "CREATE_CATALOG_AREA" as const },
	definition: {
		name: "Describe what changed and why",
		artifactKind: "scm.pull_request" as const,
		bindings: [mockPullRequestBinding],
		criteria: "Confirm the pull request explains both the change and its motivation.",
		automatedReviewPolicy: mockPullRequestPolicy,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
		areaSlug: "review-ready-work",
	},
};

const preview: CatalogAreaAdoptionPreview = {
	slug: "review-ready-work",
	definition: {
		name: "Review-ready work",
		description: "Practices that make proposed changes easier to understand and review.",
	},
	disposition: "CREATE_CATALOG_AREA",
	etag: '"area-preview"',
	actions: [
		{ slug: "describe-what-and-why", action: "ADD" },
		{ slug: "focused-changes", action: "KEEP" },
		{ slug: "clear-context", action: "BLOCKED" },
	],
	practices: [
		practice,
		{
			...practice,
			slug: "focused-changes",
			availability: "ADOPTED",
			definition: { ...practice.definition, name: "Keep changes focused" },
		},
		{
			...practice,
			slug: "clear-context",
			availability: "SLUG_CONFLICT",
			definition: { ...practice.definition, name: "Provide clear context" },
		},
	],
};

/**
 * An area states each practice's outcome on the practice's own row, so the panel needs no prose
 * explaining which of four lists a name ended up in — and no embedded copy of the definition,
 * which opens as its own level on top instead.
 */
const meta = {
	title: "Workspace admin/Practice adoption/Area panel",
	component: AreaAdoptionPanel,
	parameters: { layout: "fullscreen", chromatic: { viewports: [320, 1440] } },
	args: {
		preview,
		isLoading: false,
		isError: false,
		isPending: false,
		onRetry: fn(),
		onConfirm: fn(),
		onOpenPractice: fn(),
	},
	render: (args) => (
		<DetailDrawerStack stack={[{ kind: "area", id: "review-ready-work" }]} onClose={() => {}}>
			{() => <AreaAdoptionPanel {...args} />}
		</DetailDrawerStack>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof AreaAdoptionPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const MixedOutcomes: Story = {
	play: async ({ args }) => {
		// Only ADD and MOVE_TO_AREA are changes, so three practices produce one.
		const confirm = await screen.findByRole("button", { name: "Add 1 practice" });
		await expectSettledVisible(confirm);
		await expect(screen.getByText("Adds")).toBeVisible();
		await expect(screen.getByText("Already here")).toBeVisible();
		await expect(screen.getByText("Blocked")).toBeVisible();
		await userEvent.click(
			screen.getByRole("button", { name: "Describe what changed and why, adds" }),
		);
		await expect(args.onOpenPractice).toHaveBeenCalledWith("describe-what-and-why");
	},
};

export const RestoreDeletedArea: Story = {
	args: {
		preview: {
			...preview,
			disposition: "REUSE_EXISTING_AREA",
			actions: [
				{ slug: "describe-what-and-why", action: "MOVE_TO_AREA" },
				{ slug: "focused-changes", action: "MOVE_TO_AREA" },
				{ slug: "clear-context", action: "KEEP" },
			],
		},
	},
	play: async () => {
		// Nothing is created, so the action is a restore rather than an add.
		await expectSettledVisible(await screen.findByRole("button", { name: "Restore area" }));
		await expect(screen.getAllByText("Moves back")).toHaveLength(2);
	},
};

export const NothingToChange: Story = {
	args: {
		preview: {
			...preview,
			actions: preview.actions.map(({ slug }) => ({ slug, action: "KEEP" as const })),
		},
	},
	play: async () => {
		await expectGenuinelyDisabled(await screen.findByRole("button", { name: "Add 0 practices" }));
	},
};

export const Loading: Story = {
	args: { preview: undefined, isLoading: true },
	play: async () => {
		await expectSettledVisible(await screen.findByText("Loading area preview"));
	},
};

export const FailedToLoad: Story = {
	args: { preview: undefined, isLoading: false, isError: true, error: new Error("offline") },
	play: async ({ args }) => {
		const retry = await screen.findByRole("button", { name: "Retry" });
		await expectSettledVisible(retry);
		await userEvent.click(retry);
		await expect(args.onRetry).toHaveBeenCalledOnce();
	},
};

export const Adding: Story = {
	args: { isPending: true },
	play: async () => {
		await expectGenuinelyDisabled(await screen.findByRole("button", { name: "Adding…" }));
	},
};

export const LongContentInDarkMode: Story = {
	args: {
		preview: {
			...preview,
			definition: {
				name: "Decisions, documentation, and long-lived operational knowledge",
				description:
					"Practices covering how a team records the reasoning behind a change, keeps operational runbooks current, and makes the resulting knowledge findable long after the original authors have moved on.",
			},
		},
	},
	globals: { theme: "dark" },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};
