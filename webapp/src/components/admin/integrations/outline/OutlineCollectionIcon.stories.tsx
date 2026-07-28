import type { Meta, StoryObj } from "@storybook/react";
import { OutlineCollectionIcon } from "@/components/admin/integrations/outline/OutlineCollectionIcon";

/**
 * The resolver that turns Outline's collection `icon` string into something safe to render: a
 * Lucide equivalent for a known registry name, the raw character for an emoji, and a bare colour
 * dot for anything unknown (custom-emoji UUIDs, unrecognised names, or no icon at all) — a wire
 * value never leaks into the UI as text.
 */
const meta = {
	component: OutlineCollectionIcon,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof OutlineCollectionIcon>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A name from Outline's icon registry — resolved to its Lucide equivalent, tinted with the collection colour. */
export const NamedIcon: Story = {
	args: { icon: "beaker", color: "#0366d6" },
};

/** A raw emoji character — Outline's catch-all branch; rendered as-is. */
export const Emoji: Story = {
	args: { icon: "📚", color: "#0366d6" },
};

/** A custom-emoji UUID: the image lives behind an authenticated Outline endpoint, so it degrades to the colour dot. */
export const CustomEmojiUuid: Story = {
	args: { icon: "0d1a2f3c-4b5e-4a6d-8f90-1a2b3c4d5e6f", color: "#d73a4a" },
};

/** An unknown icon name never leaks as text — it falls back to the colour dot. */
export const UnknownName: Story = {
	args: { icon: "not-a-real-icon-name", color: "#2ea043" },
};

/** No icon at all: the colour dot alone. */
export const NoIcon: Story = {
	args: { icon: null, color: "#8250df" },
};
