import type { Meta, StoryObj } from "@storybook/react-vite";
import { noSessions, sessionsError } from "@/mocks/handlers";
import { SessionsSection } from "./SessionsSection";

/**
 * Active-sessions settings section (ADR 0017 native auth). Lists the account's
 * sessions via the `GET /user/sessions` TanStack Query hook — rendered here against
 * MSW-mocked responses (see `src/mocks/handlers.ts`) plus the global
 * `QueryClientProvider` decorator wired in `.storybook/preview.ts`.
 */
const meta = {
	component: SessionsSection,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof SessionsSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Default: three sessions from the shared fixture (one is the current device). */
export const Default: Story = {};

/** No active sessions — empty-state copy. */
export const Empty: Story = {
	parameters: { msw: { handlers: [noSessions] } },
};

/** Server error fetching sessions — error-state copy. */
export const ErrorState: Story = {
	parameters: { msw: { handlers: [sessionsError] } },
};
