import type { Meta, StoryObj } from "@storybook/react";
import { WorkspaceForbidden } from "./WorkspaceForbidden";

/**
 * Error state for workspace authorization failures (HTTP 403).
 * Use in route error boundaries when the user lacks permission to access a workspace.
 *
 * ## Accessibility
 * - Uses `role="alert"` with `aria-live="assertive"` to announce to screen readers
 * - Auto-focuses the container when mounted for keyboard navigation
 * - Icons use `aria-hidden` to avoid redundant screen reader announcements
 *
 * ## Usage
 * ```tsx
 * // In a TanStack Router route
 * errorComponent: ({ error }) => {
 *   if (error instanceof WorkspaceForbiddenError) {
 *     return <WorkspaceForbidden slug={error.slug} />;
 *   }
 *   throw error;
 * }
 * ```
 */
const meta = {
	component: WorkspaceForbidden,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		slug: {
			control: "text",
			description: "The workspace slug that was denied access",
		},
	},
} satisfies Meta<typeof WorkspaceForbidden>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Generic forbidden message without workspace identification. */
export const Default: Story = {};

/** Forbidden message with the workspace slug displayed. */
export const WithSlug: Story = {
	args: {
		slug: "private-workspace",
	},
};
