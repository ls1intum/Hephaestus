import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen } from "storybook/test";

import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { LevelCancel } from "@/components/layout/detail-drawer/LevelCancel";
import { withPageBehind } from "@/stories/decorators";
import { settledDrawerPanel } from "@/stories/overlay";
import { expectNoPanelOverflow, expectPanelContentInset } from "@/stories/reflow";
import { Stateful } from "@/stories/stateful";

import { catalogGroupLevel, GUARDED_CATALOG_LEVEL_KINDS } from "./catalog-search";
import { CatalogFormLevel } from "./CatalogFormLevel";
import { CatalogGroupForm } from "./CatalogGroupForm";
import { catalogGroup } from "./fixtures";

const group = catalogGroup();

/**
 * A level of the instance catalog's drawer stack, so the catalog stays on screen while a group is
 * written — and so these stories exercise the surface people actually get.
 */
const meta = {
	component: CatalogGroupForm,
	parameters: { layout: "fullscreen", chromatic: { viewports: [1440] } },
	decorators: [withPageBehind],
	args: { isPending: false, onSubmit: fn(), cancel: <LevelCancel /> },
	argTypes: { cancel: { control: false } },
	render: (args) => (
		<Stateful initial={[catalogGroupLevel(args.mode === "edit" ? args.group.slug : undefined)]}>
			{(stack, setStack) => (
				<DetailDrawerStack
					stack={stack}
					guardedKinds={GUARDED_CATALOG_LEVEL_KINDS}
					onClose={(depth) => setStack(stack.slice(0, depth))}
				>
					{(entry, level) => (
						<CatalogFormLevel
							kind={entry.kind}
							nested={level.nested}
							path={{
								behind: [{ label: "Practice catalog", depth: 0 }],
								onClose: (depth) => setStack(stack.slice(0, depth)),
							}}
						>
							<CatalogGroupForm {...args} />
						</CatalogFormLevel>
					)}
				</DetailDrawerStack>
			)}
		</Stateful>
	),
	tags: ["autodocs"],
} satisfies Meta<typeof CatalogGroupForm>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Create: Story = { args: { mode: "create" } };

export const Edit: Story = { args: { mode: "edit", group } };

export const HephaestusUpdateAvailable: Story = {
	play: async () => {
		// The same banner as the practice editor's, and the same way to get it wrong: rendered beside
		// `DrawerBody` instead of inside it, it lands on the unpadded panel.
		await expectPanelContentInset(await settledDrawerPanel());
	},
	args: {
		mode: "edit",
		group: catalogGroup({
			state: "UPDATE_WAITING",
			changeKind: "PRESENTATION",
			shipped: {
				name: "Review-ready work",
				description: "Make every change easy to review.",
				icon: "PackageCheck",
				color: "sky",
			},
		}),
		onUseHephaestusVersion: fn(),
	},
};

export const StaleEdit: Story = {
	args: { mode: "edit", group, conflict: true, onContinueWithDraft: fn() },
	play: async () => {
		await expect(screen.getByText("This group changed while you were editing")).toBeVisible();
		await expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();
	},
};

export const Submitting: Story = {
	args: { mode: "edit", group, isPending: true },
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
