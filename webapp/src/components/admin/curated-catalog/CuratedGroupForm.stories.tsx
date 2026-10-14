import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen } from "storybook/test";

import type { CatalogEntryStatus } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { LevelCancel } from "@/components/layout/detail-drawer/LevelCancel";
import { withPageBehind } from "@/stories/decorators";
import { settledDrawerPanel } from "@/stories/overlay";
import { expectNoPanelOverflow, expectPanelContentInset } from "@/stories/reflow";
import { Stateful } from "@/stories/stateful";

import { curatedGroupLevel, GUARDED_CURATED_LEVEL_KINDS } from "./curated-catalog-search";
import { CuratedFormLevel } from "./CuratedFormLevel";
import { CuratedGroupForm } from "./CuratedGroupForm";

const status = (overrides: Partial<CatalogEntryStatus> = {}): CatalogEntryStatus => ({
	etag: "tag",
	state: "FROM_HEPHAESTUS",
	changeKind: "NONE",
	offered: true,
	...overrides,
});

const initialData = {
	slug: "review-ready-work",
	name: "Packaging work for review",
	description: "Make a change cheap to review before you ask for one.",
	icon: "Package",
	color: "sky",
	status: status(),
};

/**
 * A level of the instance catalog's drawer stack, so the catalog stays on screen while a group is
 * written — and so these stories exercise the surface people actually get.
 */
const meta = {
	component: CuratedGroupForm,
	parameters: { layout: "fullscreen", chromatic: { viewports: [1440] } },
	decorators: [withPageBehind],
	args: { isPending: false, onSubmit: fn(), cancel: <LevelCancel /> },
	argTypes: { cancel: { control: false } },
	render: (args) => (
		<Stateful
			initial={[curatedGroupLevel(args.mode === "edit" ? args.initialData.slug : undefined)]}
		>
			{(stack, setStack) => (
				<DetailDrawerStack
					stack={stack}
					guardedKinds={GUARDED_CURATED_LEVEL_KINDS}
					onClose={(depth) => setStack(stack.slice(0, depth))}
				>
					{(entry, level) => (
						<CuratedFormLevel
							kind={entry.kind}
							nested={level.nested}
							path={{
								behind: [{ label: "Practice catalog", depth: 0 }],
								onClose: (depth) => setStack(stack.slice(0, depth)),
							}}
						>
							<CuratedGroupForm {...args} />
						</CuratedFormLevel>
					)}
				</DetailDrawerStack>
			)}
		</Stateful>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof CuratedGroupForm>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Create: Story = { args: { mode: "create" } };

export const Edit: Story = { args: { mode: "edit", initialData } };

export const HephaestusUpdateAvailable: Story = {
	play: async () => {
		// The same banner as the practice editor's, and the same way to get it wrong: rendered beside
		// `DrawerBody` instead of inside it, it lands on the unpadded panel.
		await expectPanelContentInset(await settledDrawerPanel());
	},
	args: {
		mode: "edit",
		initialData: {
			...initialData,
			status: status({ state: "UPDATE_WAITING", changeKind: "PRESENTATION" }),
		},
		onUseHephaestusVersion: fn(),
	},
};

/** A read behind the form failed to refresh. The draft stays open beside the retry. */
export const RefreshFailed: Story = {
	args: {
		mode: "edit",
		initialData,
		refreshAlert: (
			<QueryErrorAlert
				error={{ status: 503 }}
				title="We could not refresh the group"
				onRetry={fn()}
			/>
		),
	},
	play: async () => {
		await expectPanelContentInset(await settledDrawerPanel());
		await expect(screen.getByText("We could not refresh the group")).toBeVisible();
		await expect(screen.getByRole("button", { name: "Retry" })).toBeEnabled();
		await expect(screen.getByRole("textbox", { name: /Name/u })).toHaveValue(initialData.name);
	},
};

export const StaleEdit: Story = {
	args: { mode: "edit", initialData, conflict: true, onContinueWithDraft: fn() },
};

export const Submitting: Story = {
	args: { mode: "edit", initialData, isPending: true },
	play: async () => {
		await expect(screen.getByRole("textbox", { name: /Name/u })).toBeDisabled();
	},
};

export const NarrowViewport: Story = {
	args: { mode: "create" },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectNoPanelOverflow(await settledDrawerPanel());
	},
};
