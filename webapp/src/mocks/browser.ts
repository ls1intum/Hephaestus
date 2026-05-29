// MSW worker for browser environments (Storybook canvas, Storybook Vitest browser
// mode). The worker intercepts fetch via the service worker generated into
// `public/mockServiceWorker.js` (see `npx msw init public/`).

import { setupWorker } from "msw/browser";
import { handlers } from "./handlers";

export const worker = setupWorker(...handlers);
