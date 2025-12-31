import type { Meta, StoryObj } from "@storybook/react";
import { WorkspaceNotFound } from "./WorkspaceNotFound";

/**
 * Error state for workspace resolution failures (HTTP 404).
 * Use in route notFoundComponent when a workspace slug does not exist.
 *
 * ## Accessibility
 * - Uses `role="alert"` with `aria-live="assertive"` to announce to screen readers
 * - Auto-focuses the container when mounted for keyboard navigation
 * - Icons use `aria-hidden` to avoid redundant screen reader announcements
 * - Long slugs are truncated with ellipsis to prevent layout breakage
 *
 * ## Usage
 * ```tsx
 * // In a TanStack Router route
 * notFoundComponent: () => {
 *   const { workspaceSlug } = Route.useParams();
 *   return <WorkspaceNotFound slug={workspaceSlug} />;
 * }
 * ```
 */
const meta = {
	component: WorkspaceNotFound,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		slug: {
			control: "text",
			description: "The workspace slug that was not found",
		},
		showRetry: {
			control: "boolean",
			description: "Whether to show a retry button for transient errors",
		},
	},
} satisfies Meta<typeof WorkspaceNotFound>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Generic not-found message without workspace identification. */
export const Default: Story = {};

/** Not-found message with the workspace slug displayed. */
export const WithSlug: Story = {
	args: {
		slug: "my-team-workspace",
	},
};

/** Not-found message with a retry option for potential transient errors. */
export const WithRetry: Story = {
	args: {
		slug: "my-team-workspace",
		showRetry: true,
	},
};

/** Extremely long slug is truncated to prevent layout issues. Title attribute shows full slug on hover. */
export const LongSlug: Story = {
	args: {
		slug: "this-is-an-extremely-long-workspace-slug-that-would-normally-break-the-layout-if-not-properly-handled",
	},
};

/** Special characters in slug are handled safely. */
export const SpecialCharacters: Story = {
	args: {
		slug: "test-workspace-<script>alert('xss')</script>",
	},
};
