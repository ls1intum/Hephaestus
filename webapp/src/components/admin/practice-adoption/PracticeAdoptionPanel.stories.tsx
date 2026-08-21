import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import type { CatalogPracticePreview } from "@/api/types.gen";
import { DetailDrawerStack } from "@/components/core/detail-drawer/DetailDrawerStack";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { withPageBehind } from "@/stories/decorators";
import { Stateful } from "@/stories/stateful";
import { expectGenuinelyDisabled } from "@/test/controls";
import { expectSettledVisible } from "@/test/overlay";
import { PracticeAdoptionPanel, type PracticeAdoptionState } from "./PracticeAdoptionPanel";

const preview: CatalogPracticePreview = {
	slug: "describe-what-and-why",
	availability: "AVAILABLE",
	etag: '"adoption-plan"',
	initialAutonomy: "HUMAN_APPROVAL",
	sourceReviewRuleFingerprint: mockAuthorDeclaredEvidenceValidation.reviewRuleFingerprint,
	area: {
		slug: "review-ready-work",
		disposition: "CREATE_CATALOG_AREA",
		definition: { name: "Review-ready work", description: "Work prepared for useful review." },
	},
	definition: {
		name: "Describe what changed and why",
		artifactKind: "scm.pull_request",
		bindings: [mockPullRequestBinding],
		criteria: "Confirm the pull request explains both the change and its motivation.",
		automatedReviewPolicy: mockPullRequestPolicy,
		automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
		precomputeScript: "export default { hasDescription: pullRequest.body.length > 0 };",
		whyItMatters: "Reviewers need intent to assess whether the change solves the right problem.",
		whatGoodLooksLike: "A concise summary, motivation, and verification steps.",
		areaSlug: "review-ready-work",
	},
};

type ReadyState = Extract<PracticeAdoptionState, { status: "ready" }>;

const ready = (over: Partial<ReadyState> = {}): ReadyState => ({
	status: "ready",
	preview,
	definitionOptions: mockPracticeDefinitionOptions,
	action: "idle",
	...over,
});

/**
 * The panel has no page of its own — the route it used to occupy redirects here with the drawer
 * already open — so every story mounts a real drawer over a real page. That is also what makes the
 * dismiss testable: the stack is stateful, so Escape, an outside press and the header control all
 * actually close it instead of firing an inert spy.
 */
const meta = {
	title: "Workspace admin/Practice adoption/Practice panel",
	component: PracticeAdoptionPanel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	args: {
		workspaceSlug: "demo",
		state: ready(),
		onAdopt: fn(),
	},
	argTypes: {
		// A discriminated union renders as a free-text box, which cannot produce a valid value.
		state: { control: false },
	},
	render: (args) => (
		<Stateful initial={[{ kind: "practice", id: preview.slug }]}>
			{(stack, setStack) => (
				<DetailDrawerStack stack={stack} onClose={(depth) => setStack(stack.slice(0, depth))}>
					{(_entry, level) => <PracticeAdoptionPanel {...args} nested={level.nested} />}
				</DetailDrawerStack>
			)}
		</Stateful>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeAdoptionPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Available: Story = {
	play: async ({ args }) => {
		const adopt = await screen.findByRole("button", { name: "Add practice" });
		await expectSettledVisible(adopt);
		await expect(screen.getByText("Review before sending")).toBeVisible();
		await expect(screen.getByText("Create “Review-ready work”")).toBeVisible();
		await userEvent.click(adopt);
		await expect(args.onAdopt).toHaveBeenCalledOnce();
	},
};

export const DismissReturnsToThePage: Story = {
	play: async () => {
		await expectSettledVisible(await screen.findByRole("button", { name: "Add practice" }));
		// The top level returns to the page, so it closes rather than stepping back one drawer.
		await userEvent.click(screen.getByRole("button", { name: "Close" }));
		await expect(await screen.findByRole("heading", { name: "Practice setup" })).toBeVisible();
		await expect(screen.queryByRole("button", { name: "Add practice" })).not.toBeInTheDocument();
	},
};

export const ReusesExistingArea: Story = {
	args: {
		state: ready({
			preview: { ...preview, area: { ...preview.area, disposition: "REUSE_EXISTING_AREA" } },
		}),
	},
	play: async () => {
		await expectSettledVisible(await screen.findByText("Join “Review-ready work”"));
	},
};

export const AlreadyAdded: Story = {
	args: { state: ready({ preview: { ...preview, availability: "ADOPTED" } }) },
	play: async () => {
		const open = await screen.findByRole("link", { name: "Open workspace practice" });
		await expectSettledVisible(open);
		await expect(screen.queryByRole("button", { name: "Add practice" })).not.toBeInTheDocument();
		await expect(open).toHaveAttribute("href", "/w/demo/admin/practices/describe-what-and-why");
	},
};

export const NameUnavailable: Story = {
	args: { state: ready({ preview: { ...preview, availability: "SLUG_CONFLICT" } }) },
	play: async () => {
		// Words, colour and icon all come from one registry entry, so the alert and the chip agree.
		await expectSettledVisible(await screen.findByRole("alert"));
		await expect(screen.getAllByText("Name unavailable")).toHaveLength(2);
		await expect(screen.queryByRole("button", { name: "Add practice" })).not.toBeInTheDocument();
	},
};

export const Adding: Story = {
	args: { state: ready({ action: "adding" }) },
	play: async () => {
		await expectGenuinelyDisabled(await screen.findByRole("button", { name: "Adding…" }));
	},
};

export const PreviewWentStale: Story = {
	args: { state: ready({ action: "stale" }) },
	play: async () => {
		await expectSettledVisible(
			await screen.findByText("The library changed while you were reading"),
		);
	},
};

export const UnassignedAndOff: Story = {
	args: {
		state: ready({
			preview: { ...preview, initialAutonomy: "OFF", area: { disposition: "UNASSIGNED" } },
		}),
	},
	play: async () => {
		await expectSettledVisible(await screen.findByText("Belong to no area"));
		await expect(screen.getByText("Off")).toBeVisible();
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async () => {
		await expectSettledVisible(await screen.findByText("Loading adoption preview"));
	},
};

export const FailedToLoad: Story = {
	args: {
		state: { status: "error", error: new Error("offline"), onRetry: fn() },
	},
	play: async () => {
		await expectSettledVisible(await screen.findByRole("button", { name: "Retry" }));
	},
};

export const LongContent: Story = {
	args: {
		state: ready({
			preview: {
				...preview,
				definition: {
					...preview.definition,
					criteria:
						"Confirm that the pull request explains the behavior change, the operational constraints that shaped it, the alternatives considered, and the evidence used to verify the result. Stay silent when the change is generated automatically and no meaningful author decision exists.",
				},
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
