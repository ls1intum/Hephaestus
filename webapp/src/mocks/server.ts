// Vitest and Storybook share these handlers so mocked behavior stays consistent.

import { setupServer } from "msw/node";

import { handlers } from "./handlers";

export const server = setupServer(...handlers);
