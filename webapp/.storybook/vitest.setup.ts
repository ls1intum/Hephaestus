import { setProjectAnnotations } from '@storybook/react-vite';
import * as projectAnnotations from './preview';

// This is an important step to apply the right configuration when testing your stories.
// More info at: https://storybook.js.org/docs/api/portable-stories/portable-stories-vitest#setprojectannotations
setProjectAnnotations([projectAnnotations]);

// NOTE on MSW in the Storybook Vitest run:
// This project runs stories in Vitest *browser mode* (chromium — see
// `vitest.config.storybook.ts`). Network interception there is handled by the MSW
// *browser worker*, initialized via `initialize()` + `mswLoader` in
// `.storybook/preview.ts` (which also serves per-story `parameters.msw.handlers`).
// The Node `setupServer` (`src/mocks/server.ts`) must NOT be imported here: it pulls
// in `node:http`, which Vite externalizes for the browser and crashes the run. Use
// `src/mocks/server.ts` from Node-environment tests under `vitest.config.ts` instead.
