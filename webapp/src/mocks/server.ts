// MSW server for Node environments (Vitest jsdom). The same request handlers back the
// Storybook browser worker (see `.storybook/preview.tsx`), so mocked auth behaviour is
// identical across stories and unit tests.
//
// `src/test/setup-msw.ts` starts, resets and closes it around the Vitest run.

import { setupServer } from "msw/node";
import { handlers } from "./handlers";

export const server = setupServer(...handlers);
