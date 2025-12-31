import type { Meta, StoryObj } from "@storybook/react";
import { WorkspaceError } from "./WorkspaceError";

/**
 * Generic error state for unexpected workspace errors.
 * Use in route error boundaries when the error isn't a specific known type (404, 403).
 *
 * ## Accessibility
 * - Uses `role="alert"` with `aria-live="assertive"` to announce to screen readers
 * - Auto-focuses the container when mounted for keyboard navigation
 * - Icons use `aria-hidden` to avoid redundant screen reader announcements
 *
 * ## Usage
 * ```tsx
 * // In a TanStack Router route
 * errorComponent: ({ error, reset }) => {
 *   if (error instanceof WorkspaceForbiddenError) {
 *     return <WorkspaceForbidden slug={error.slug} />;
 *   }
 *   return <WorkspaceError error={error} reset={reset} />;
 * }
 * ```
 */
const meta = {
	component: WorkspaceError,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		error: {
			control: false,
			description: "The error object that occurred",
		},
		reset: {
			control: false,
			description: "Optional reset function from error boundary",
		},
	},
} satisfies Meta<typeof WorkspaceError>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Network-related error with retry messaging. */
export const NetworkError: Story = {
	args: {
		error: new Error("Failed to load workspace: network error"),
	},
};

/** Generic unexpected error. */
export const UnexpectedError: Story = {
	args: {
		error: new Error("Something unexpected happened"),
	},
};

/** Error with a reset function available. */
export const WithReset: Story = {
	args: {
		error: new Error("Connection timeout"),
		reset: () => {
			console.log("Reset triggered");
		},
	},
};
