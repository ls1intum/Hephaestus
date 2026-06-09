// MSW server for Node environments (Vitest jsdom). The same request handlers back the
// Storybook browser worker (see `.storybook/preview.ts`), so mocked auth behaviour is
// identical across stories and unit tests.
//
// Wired into Vitest via `src/test/setup-msw.ts` (registered as a `setupFiles` entry in
// `vite.config.ts`): `beforeAll(server.listen)`, `afterEach(server.resetHandlers)`,
// `afterAll(server.close)`.

import { setupServer } from "msw/node";
import { handlers } from "./handlers";

export const server = setupServer(...handlers);
