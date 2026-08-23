import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";
import type { CatalogEntryStatus } from "@/api/types.gen";
import { withStandardPage } from "@/stories/decorators";
import { expectNoOverflowingElement } from "@/test/reflow";
import { CuratedAreaForm } from "./CuratedAreaForm";

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

const meta = {
	title: "Instance admin/Practice catalog/Area editor",
	component: CuratedAreaForm,
	parameters: { layout: "fullscreen", chromatic: { viewports: [1440] } },
	decorators: [withStandardPage],
	args: { isPending: false, onSubmit: fn() },
	tags: ["autodocs"],
} satisfies Meta<typeof CuratedAreaForm>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Create: Story = { args: { mode: "create" } };

export const Edit: Story = { args: { mode: "edit", initialData } };

export const HephaestusUpdateAvailable: Story = {
	args: {
		mode: "edit",
		initialData: {
			...initialData,
			status: status({ state: "UPDATE_WAITING", changeKind: "PRESENTATION" }),
		},
		onUseHephaestusVersion: fn(),
	},
};

export const StaleEdit: Story = {
	args: { mode: "edit", initialData, conflict: true, onContinueWithDraft: fn() },
};

export const Submitting: Story = {
	args: { mode: "edit", initialData, isPending: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("textbox", { name: /Name/ })).toBeDisabled();
	},
};

export const NarrowViewport: Story = {
	args: { mode: "create" },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvasElement }) => {
		await expectNoOverflowingElement(canvasElement);
	},
};
