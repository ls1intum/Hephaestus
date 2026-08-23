import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent } from "storybook/test";
import { DetailDrawerStack } from "@/components/core/detail-drawer/DetailDrawerStack";
import { LevelCancel } from "@/components/core/detail-drawer/LevelCancel";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { withPageBehind } from "@/stories/decorators";
import { Stateful } from "@/stories/stateful";
import { expectSettledVisible } from "@/test/overlay";
import { expectNoPanelOverflow } from "@/test/reflow";
import { CuratedFormLevel } from "./CuratedFormLevel";
import { CuratedPracticeForm, type CuratedPracticeFormInitialValue } from "./CuratedPracticeForm";
import { curatedPracticeLevel, GUARDED_CURATED_LEVEL_KINDS } from "./curated-catalog-search";

const areas = [
	{ slug: "communication", name: "Communication" },
	{ slug: "version-control", name: "Version control" },
];

const initialData: CuratedPracticeFormInitialValue = {
	slug: "clear-pr-description",
	name: "Write a clear pull request description",
	areaSlug: "communication",
	bindings: [mockPullRequestBinding],
	criteria: "Review whether the description explains the purpose, approach, and testing.",
	whyItMatters: "Reviewers should not need to reconstruct the author's intent.",
	whatGoodLooksLike: "The description states why, what changed, and how it was verified.",
	precomputeScript: "export default function precompute() { return {}; }",
	automatedReviewPolicy: mockPullRequestPolicy,
	automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	status: {
		etag: "tag",
		state: "FROM_HEPHAESTUS" as const,
		changeKind: "NONE" as const,
		offered: true,
	},
};

const meta = {
	title: "Instance admin/Practice catalog/Practice editor",
	component: CuratedPracticeForm,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [1440] },
	},
	decorators: [withPageBehind],
	tags: ["autodocs"],
	args: { definitionOptions: mockPracticeDefinitionOptions, cancel: <LevelCancel /> },
	argTypes: { cancel: { control: false } },
	render: (args) => (
		<Stateful
			initial={[curatedPracticeLevel(args.mode === "edit" ? args.initialData.slug : undefined)]}
		>
			{(stack, setStack) => (
				<DetailDrawerStack
					stack={stack}
					guardedKinds={GUARDED_CURATED_LEVEL_KINDS}
					onClose={(depth) => setStack(stack.slice(0, depth))}
				>
					{(entry, level) => (
						<CuratedFormLevel kind={entry.kind} nested={level.nested}>
							<CuratedPracticeForm {...args} />
						</CuratedFormLevel>
					)}
				</DetailDrawerStack>
			)}
		</Stateful>
	),
} satisfies Meta<typeof CuratedPracticeForm>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Create: Story = {
	args: {
		mode: "create",
		areas,
		isPending: false,
		onSubmit: fn(),
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async () => {
		const [popup] = document.querySelectorAll<HTMLElement>('[data-slot="drawer-popup"]');
		await expectSettledVisible(popup);
		await expectNoPanelOverflow(popup);
	},
};

export const Edit: Story = {
	args: {
		mode: "edit",
		initialData,
		areas,
		isPending: false,
		onSubmit: fn(),
	},
};

export const StaleEdit: Story = {
	args: {
		mode: "edit",
		initialData,
		areas,
		isPending: false,
		conflict: true,
		onContinueWithDraft: fn(),
		onSubmit: fn(),
	},
	play: async () => {
		await expect(screen.getByText("This practice changed while you were editing")).toBeVisible();
		await expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();
	},
};

export const HephaestusUpdateAvailable: Story = {
	args: {
		mode: "edit",
		initialData: {
			...initialData,
			status: {
				...initialData.status,
				state: "UPDATE_WAITING" as const,
				changeKind: "DETECTION" as const,
			},
			shipped: {
				name: "Say what changed and why",
				artifactKind: "scm.pull_request",
				bindings: [mockPullRequestBinding],
				criteria: "The updated default criteria",
				automatedReviewPolicy: mockPullRequestPolicy,
				automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
				whyItMatters: "So a reviewer can start from intent rather than diff archaeology.",
			},
		},
		areas,
		isPending: false,
		onUseHephaestusVersion: fn(),
		onKeepCurrentDefinition: fn(),
		onSubmit: fn(),
	},
	play: async () => {
		// The full label, since colour alone cannot carry which kind of update it is.
		await expect(screen.getByText("Hephaestus update available: review rules")).toBeVisible();
		await expect(screen.getByText(/would change review rules/)).toBeVisible();
		await expect(screen.getByRole("button", { name: "Review Hephaestus update" })).toBeVisible();
		await expect(screen.getByRole("button", { name: "Apply Hephaestus update" })).toBeVisible();
		await expect(screen.getByRole("button", { name: "Keep saved version" })).toBeVisible();
		await userEvent.click(screen.getByRole("button", { name: "Review Hephaestus update" }));
		await expect(screen.getByText("Unassigned")).toBeVisible();
		await expect(screen.getAllByText("Not set").length).toBeGreaterThan(0);
	},
};

export const ValidationErrors: Story = {
	args: {
		mode: "create",
		areas,
		isPending: false,
		onSubmit: fn(),
	},
	parameters: { chromatic: { disableSnapshot: true } },
	play: async () => {
		await userEvent.click(screen.getByRole("button", { name: "Create practice" }));
		await expect(screen.getByText("Name must be at least 3 characters")).toBeVisible();
		await expect(screen.queryByText("Select at least one trigger event")).not.toBeInTheDocument();
		await expect(screen.getByRole("textbox", { name: /Name/ })).toHaveAttribute(
			"aria-describedby",
			"practice-name-error",
		);
	},
};

export const Submitting: Story = {
	args: {
		mode: "edit",
		initialData,
		areas,
		isPending: true,
		onSubmit: fn(),
	},
	play: async () => {
		await expect(screen.getByRole("textbox", { name: /Name/ })).toBeDisabled();
	},
};
