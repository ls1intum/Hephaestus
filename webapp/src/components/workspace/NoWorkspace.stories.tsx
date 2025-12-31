import type { Meta, StoryObj } from "@storybook/react";
import { NoWorkspace } from "./NoWorkspace";

/**
 * Empty state for users without workspace membership.
 * Use when redirecting authenticated users who have no associated workspaces.
 *
 * ## Accessibility
 * - Uses `role="status"` with `aria-live="polite"` for non-critical announcements
 * - Auto-focuses the container when mounted for keyboard navigation
 * - Icons use `aria-hidden` to avoid redundant screen reader announcements
 *
 * ## Usage
 * ```tsx
 * // In a route component
 * if (workspaces.length === 0) {
 *   return <NoWorkspace />;
 * }
 * ```
 */
const meta = {
	component: NoWorkspace,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
} satisfies Meta<typeof NoWorkspace>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Standard presentation for users with no workspace memberships. */
export const Default: Story = {};
