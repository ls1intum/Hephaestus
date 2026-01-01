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
 * - aria-atomic ensures the entire message is read as a unit
 *
 * ## Error Detection
 * The component uses `error.name` (TypeError, NetworkError) and message pattern matching
 * to detect network-related errors and show appropriate retry messaging.
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

/** Generic unexpected error shows standard message. */
export const Default: Story = {
	args: {
		error: new Error("Something unexpected happened"),
	},
};

/** Network error detected shows retry-friendly message. */
export const NetworkError: Story = {
	args: {
		error: (() => {
			const err = new Error("Failed to fetch");
			err.name = "TypeError";
			return err;
		})(),
	},
};

/** Error with a reset function available. */
export const WithReset: Story = {
	args: {
		error: new Error("Request failed"),
		reset: () => console.log("Reset triggered"),
	},
};
