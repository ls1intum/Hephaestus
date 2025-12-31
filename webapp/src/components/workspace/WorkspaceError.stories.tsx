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

/** TypeError (fetch failures) shows network-friendly messaging. */
export const FetchTypeError: Story = {
	args: {
		error: (() => {
			const err = new Error("Failed to fetch");
			err.name = "TypeError";
			return err;
		})(),
	},
};

/** Network error detected via error.name shows retry-friendly message. */
export const NetworkError: Story = {
	args: {
		error: (() => {
			const err = new Error("Network request failed");
			err.name = "NetworkError";
			return err;
		})(),
	},
};

/** Timeout error detected via message pattern. */
export const TimeoutError: Story = {
	args: {
		error: new Error("Request timeout after 30000ms"),
	},
};

/** Connection error detected via message pattern. */
export const ConnectionError: Story = {
	args: {
		error: new Error("Connection refused: ECONNREFUSED"),
	},
};

/** Generic unexpected error shows standard message. */
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

/** Server error (500) without network indicators. */
export const ServerError: Story = {
	args: {
		error: new Error("Internal Server Error"),
	},
};
