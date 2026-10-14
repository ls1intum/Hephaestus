import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, waitFor } from "storybook/test";

import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { LevelCancel } from "@/components/layout/detail-drawer/LevelCancel";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { withPageBehind } from "@/stories/decorators";
import { settledDrawerPanel } from "@/stories/overlay";
import { expectNoPanelOverflow, expectPanelContentInset } from "@/stories/reflow";
import { Stateful } from "@/stories/stateful";

import { mockDefinitionOptions } from "@/components/admin/practice-editor/fixtures";

import { mockGroups, mockScriptedPractice } from "./fixtures";
import { GUARDED_LEVEL_KINDS, practiceFormLevel } from "./practice-search";
import { PracticeForm } from "./PracticeForm";
import { PracticeFormLevel } from "./PracticeFormLevel";

const createSubmit = fn();
const editSubmit = fn();

/**
 * The editor is a level of the practice-setup drawer stack, so the tree a practice belongs to stays
 * on screen while it is written — and so these stories exercise the surface people actually get.
 * The fields are the shared editor's, whose own stories cover them; these cover what the workspace
 * wraps around it and what it sends.
 */
const meta = {
	component: PracticeForm,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	decorators: [withPageBehind],
	args: {
		mode: "create",
		workspaceSlug: "demo",
		groups: mockGroups,
		definitionOptions: mockDefinitionOptions,
		onSubmit: createSubmit,
		isPending: false,
		cancel: <LevelCancel />,
	},
	argTypes: { cancel: { control: false } },
	render: (args) => (
		<Stateful
			initial={[practiceFormLevel(args.mode === "edit" ? args.initialData.slug : undefined)]}
		>
			{(stack, setStack) => (
				<DetailDrawerStack
					stack={stack}
					guardedKinds={GUARDED_LEVEL_KINDS}
					onClose={(depth) => setStack(stack.slice(0, depth))}
				>
					{(entry, level) => (
						<PracticeFormLevel
							nested={level.nested}
							path={{
								behind: [{ label: "Practice setup", depth: 0 }],
								onClose: (depth) => setStack(stack.slice(0, depth)),
							}}
							creating={entry.kind === "practice-new"}
						>
							<PracticeForm {...args} />
						</PracticeFormLevel>
					)}
				</DetailDrawerStack>
			)}
		</Stateful>
	),
} satisfies Meta<typeof PracticeForm>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Create: Story = {
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 1440] },
	},
	play: async () => {
		// The level is the full viewport at 320px, so the longest form in the app has to fit it by
		// scrolling down and never across.
		await expectNoPanelOverflow(await settledDrawerPanel());
		await expect(screen.queryByText("What the reviews recorded")).not.toBeInTheDocument();
	},
};

export const EscapeLeavesACleanEditor: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async () => {
		await settledDrawerPanel();

		// Nothing typed, so nothing to ask about: Escape leaves, exactly as it does on a read-only
		// panel. An editor that swallowed the gesture instead would be indistinguishable from a
		// broken drawer. `-adoption-route.test.tsx` owns the half where a draft exists.
		await userEvent.keyboard("{Escape}");
		await waitFor(async () =>
			expect(document.querySelectorAll('[data-slot="drawer-popup"]')).toHaveLength(0),
		);
	},
};

/** An edit says what the save creates, and leads to the results that stay with the practice. */
export const Edit: Story = {
	args: { mode: "edit", initialData: mockScriptedPractice, onSubmit: editSubmit },
	play: async () => {
		await settledDrawerPanel();
		await expect(screen.getByText(/the change becomes version 2/u)).toBeVisible();
		await expect(
			screen.getByRole("link", { name: "See review outcomes" }).getAttribute("href"),
		).toMatch(/^\/w\/demo\/admin\/practices\/reviews\/results\?.*commit-discipline/u);
		await expect(screen.getByRole("link", { name: "AI models" })).toHaveAttribute(
			"href",
			"/w/demo/admin/models",
		);
	},
};

/** A save refused because someone saved first: the host's notice sits above the kept draft. */
export const EditConflict: Story = {
	args: {
		mode: "edit",
		initialData: mockScriptedPractice,
		onSubmit: editSubmit,
		notice: (
			<Alert variant="warning" role="alert">
				<AlertTitle>Someone saved a newer version</AlertTitle>
				<AlertDescription>Your changes are still here. Nothing was saved.</AlertDescription>
			</Alert>
		),
	},
	play: async () => {
		await settledDrawerPanel();
		await expect(screen.getByRole("alert")).toHaveTextContent(/Nothing was saved/u);
	},
};

/** A read behind the form failed to refresh. The draft stays open beside the retry. */
export const RefreshFailed: Story = {
	args: {
		mode: "edit",
		initialData: mockScriptedPractice,
		onSubmit: fn(),
		refreshAlert: (
			<QueryErrorAlert
				error={{ status: 503 }}
				title="We could not refresh the practice editor"
				onRetry={fn()}
			/>
		),
	},
	play: async () => {
		await expectPanelContentInset(await settledDrawerPanel());
		await expect(screen.getByText("We could not refresh the practice editor")).toBeVisible();
		await expect(screen.getByRole("button", { name: "Retry" })).toBeEnabled();
		await expect(screen.getByRole("textbox", { name: /Name/u })).toHaveValue(
			mockScriptedPractice.currentVersion.definition?.name,
		);
	},
};

export const Submitting: Story = {
	args: { isPending: true, onSubmit: fn() },
	play: async () => {
		await settledDrawerPanel();
		await expect(screen.getByRole("textbox", { name: /Name/u })).toBeDisabled();
	},
};

/** A new practice sends its kind and its definition, with no position: the server appends it. */
export const CreateSends: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async () => {
		await settledDrawerPanel();
		createSubmit.mockClear();
		await userEvent.type(screen.getByRole("textbox", { name: /Name/u }), "Clear review context");
		await userEvent.type(
			screen.getByRole("textbox", { name: /What to look for/u }),
			"Check whether the reviewed work explains its purpose.",
		);
		await userEvent.click(screen.getByRole("button", { name: "Create practice" }));
		await expect(createSubmit).toHaveBeenCalledOnce();
		await expect(createSubmit.mock.lastCall?.[0]).toMatchObject({
			slug: "clear-review-context",
			kind: "scm.pull_request",
			position: null,
			definition: {
				name: "Clear review context",
				criteria: "Check whether the reviewed work explains its purpose.",
			},
		});
	},
};
