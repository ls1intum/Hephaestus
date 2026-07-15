import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { OutlineIntegrationContent } from "./OutlineIntegrationContent";

/**
 * The compositional wrapper for the Outline detail page: the connect card always renders, and the
 * mirrored-collections section is stacked below it only once `collectionsProps` is supplied (i.e.
 * the connection is live). The two states pin exactly that: connect-only when disconnected, and the
 * full two-plane layout when connected.
 */
const meta = {
	component: OutlineIntegrationContent,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		connectCardProps: {
			connected: false,
			onConnect: fn(),
			onDisconnect: fn(),
			onSyncNow: fn(),
			onCancel: fn(),
		},
	},
} satisfies Meta<typeof OutlineIntegrationContent>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Disconnected — only the connect card renders; the collections plane is absent. */
export const Disconnected: Story = {};

/** Connected — the connect card plus the mirrored-collections section (empty here). */
export const Connected: Story = {
	args: {
		connectCardProps: {
			connected: true,
			connectionState: "ACTIVE",
			connectionLabel: "app.getoutline.com",
			status: {
				documentCount: 128,
				lastSyncedAt: new Date("2026-07-14T10:00:00Z"),
				syncRunning: false,
				erroredCollections: 0,
				webhookRegistered: true,
			},
			onConnect: fn(),
			onDisconnect: fn(),
			onSyncNow: fn(),
			onCancel: fn(),
		},
		collectionsProps: {
			workspaceSlug: "demo",
			collections: [],
			isLoading: false,
			onRegisterCollection: fn(),
			onUpdateCollectionState: fn(),
			onRemoveCollection: fn(),
		},
	},
};
