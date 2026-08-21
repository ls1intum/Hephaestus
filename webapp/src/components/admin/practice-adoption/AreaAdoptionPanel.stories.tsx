import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import type { CatalogAreaAdoptionPreview } from "@/api/types.gen";
import { DetailDrawerStack } from "@/components/core/detail-drawer/DetailDrawerStack";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { withPageBehind } from "@/stories/decorators";
import { Stateful } from "@/stories/stateful";
import { expectGenuinelyDisabled } from "@/test/controls";
import { expectSettledVisible } from "@/test/overlay";
import { AreaAdoptionPanel, type AreaAdoptionState } from "./AreaAdoptionPanel";

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

const ready = (over: Partial<CatalogAreaAdoptionPreview> = {}, adding = false) =>
	({ status: "ready", preview: { ...preview, ...over }, adding }) as const;

/**
 * An area states each practice's outcome on the practice's own row, so the panel needs no prose
 * explaining which of four lists a name ended up in — and no embedded copy of the definition, which
 * opens as its own level on top instead.
 */
const meta = {
	title: "Workspace admin/Practice adoption/Area panel",
	component: AreaAdoptionPanel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	args: {
		state: ready(),
		onConfirm: fn(),
		onOpenPractice: fn(),
	},
	argTypes: {
		// A discriminated union renders as a free-text box, which cannot produce a valid value.
		state: { control: false },
	},
	render: (args) => (
		<Stateful initial={[{ kind: "area", id: preview.slug }]}>
			{(stack, setStack) => (
				<DetailDrawerStack stack={stack} onClose={(depth) => setStack(stack.slice(0, depth))}>
					{(_entry, level) => <AreaAdoptionPanel {...args} nested={level.nested} />}
				</DetailDrawerStack>
			)}
		</Stateful>
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
		await userEvent.click(screen.getByRole("button", { name: /Describe what changed and why/ }));
		await expect(args.onOpenPractice).toHaveBeenCalledWith("describe-what-and-why");
	},
};

export const RestoreDeletedArea: Story = {
	args: {
		state: ready({
			disposition: "REUSE_EXISTING_AREA",
			actions: [
				{ slug: "describe-what-and-why", action: "MOVE_TO_AREA" },
				{ slug: "focused-changes", action: "MOVE_TO_AREA" },
				{ slug: "clear-context", action: "KEEP" },
			],
		}),
	},
	play: async () => {
		// Nothing is created, so the action is a restore rather than an add.
		await expectSettledVisible(await screen.findByRole("button", { name: "Restore area" }));
		await expect(screen.getAllByText("Moves back")).toHaveLength(2);
	},
};

export const NothingToChange: Story = {
	args: {
		state: ready({
			actions: preview.actions.map(({ slug }) => ({ slug, action: "KEEP" as const })),
		}),
	},
	play: async () => {
		await expectGenuinelyDisabled(await screen.findByRole("button", { name: "Add 0 practices" }));
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async () => {
		await expectSettledVisible(await screen.findByText("Loading area preview"));
		// The header cannot invent an area colour for a slug that has not loaded.
		await expect(screen.getByRole("heading", { name: "Practice area" })).toBeVisible();
	},
};

export const FailedToLoad: Story = {
	args: { state: { status: "error", error: new Error("offline"), onRetry: fn() } },
	play: async ({ args }) => {
		const retry = await screen.findByRole("button", { name: "Retry" });
		await expectSettledVisible(retry);
		await userEvent.click(retry);
		await expect(
			(args.state as Extract<AreaAdoptionState, { status: "error" }>).onRetry,
		).toHaveBeenCalledOnce();
	},
};

export const Adding: Story = {
	args: { state: ready({}, true) },
	play: async () => {
		await expectGenuinelyDisabled(await screen.findByRole("button", { name: "Adding…" }));
	},
};

export const LongContent: Story = {
	args: {
		state: ready({
			definition: {
				name: "Decisions, documentation, and long-lived operational knowledge",
				description:
					"Practices covering how a team records the reasoning behind a change, keeps operational runbooks current, and makes the resulting knowledge findable long after the original authors have moved on.",
			},
		}),
	},
};

export const NarrowViewport: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};

export const DarkMode: Story = {
	globals: { theme: "dark" },
};
