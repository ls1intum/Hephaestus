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
import { expectGenuinelyDisabled } from "@/test/controls";
import { expectSettledVisible } from "@/test/overlay";
import { PracticeAdoptionPanel } from "./PracticeAdoptionPanel";

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

/**
 * The panel only renders inside a drawer level, so every story mounts one. That is also what the
 * product does: this surface has no page of its own, and the route it used to occupy now redirects
 * here with the drawer already open.
 */
const meta = {
	title: "Workspace admin/Practice adoption/Practice panel",
	component: PracticeAdoptionPanel,
	parameters: { layout: "fullscreen", chromatic: { viewports: [320, 1440] } },
	args: {
		workspaceSlug: "demo",
		preview,
		definitionOptions: mockPracticeDefinitionOptions,
		onAdopt: fn(),
		isPending: false,
	},
	render: (args) => (
		<DetailDrawerStack stack={[{ kind: "practice", id: args.preview.slug }]} onClose={() => {}}>
			{() => <PracticeAdoptionPanel {...args} />}
		</DetailDrawerStack>
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
		await expect(screen.getByText("Creates “Review-ready work”")).toBeVisible();
		// The top level dismisses the whole stack, so it closes rather than going back one drawer.
		await expect(screen.getByRole("button", { name: "Close" })).toBeEnabled();
		await userEvent.click(adopt);
		await expect(args.onAdopt).toHaveBeenCalledOnce();
	},
};

export const ReusesExistingArea: Story = {
	args: {
		preview: { ...preview, area: { ...preview.area, disposition: "REUSE_EXISTING_AREA" } },
	},
	play: async () => {
		await expectSettledVisible(await screen.findByText("Uses “Review-ready work”"));
	},
};

export const AlreadyAdded: Story = {
	args: { preview: { ...preview, availability: "ADOPTED" } },
	play: async () => {
		const open = await screen.findByRole("link", { name: "Open workspace practice" });
		await expectSettledVisible(open);
		await expect(screen.queryByRole("button", { name: "Add practice" })).not.toBeInTheDocument();
		await expect(open).toHaveAttribute("href", "/w/demo/admin/practices/describe-what-and-why");
	},
};

export const NameUnavailable: Story = {
	args: { preview: { ...preview, availability: "SLUG_CONFLICT" } },
	play: async () => {
		await expectSettledVisible(await screen.findByText("Name unavailable"));
		await expect(screen.queryByRole("button", { name: "Add practice" })).not.toBeInTheDocument();
	},
};

export const Adding: Story = {
	args: { isPending: true },
	play: async () => {
		await expectGenuinelyDisabled(await screen.findByRole("button", { name: "Adding…" }));
	},
};

export const PreviewWentStale: Story = {
	args: { isStale: true },
	play: async () => {
		await expectSettledVisible(
			await screen.findByText("The library changed while you were reading"),
		);
	},
};

export const UnassignedAndOff: Story = {
	args: { preview: { ...preview, initialAutonomy: "OFF", area: { disposition: "UNASSIGNED" } } },
	play: async () => {
		await expectSettledVisible(await screen.findByText("No area"));
		await expect(screen.getByText("Off")).toBeVisible();
	},
};

export const LongContentInDarkMode: Story = {
	args: {
		preview: {
			...preview,
			definition: {
				...preview.definition,
				criteria:
					"Confirm that the pull request explains the behavior change, the operational constraints that shaped it, the alternatives considered, and the evidence used to verify the result. Stay silent when the change is generated automatically and no meaningful author decision exists.",
			},
		},
	},
	globals: { theme: "dark" },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};
