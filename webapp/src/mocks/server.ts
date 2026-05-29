// MSW server for Node environments (Vitest, including the Storybook Vitest run when
// it executes outside the browser provider). Request handlers are shared with the
// browser worker so mocked behaviour is identical across both.

import { setupServer } from "msw/node";
import { handlers } from "./handlers";

export const server = setupServer(...handlers);
