// Global Vitest setup: stand up the MSW Node server so RTL tests of the query-driven auth
// components hit deterministic mocked endpoints (the same handlers Storybook uses).
//
// `onUnhandledRequest: "bypass"` keeps tests that issue no network calls unaffected — only
// the explicitly-mocked auth endpoints are intercepted. Handlers reset after each test so a
// `server.use(...)` override in one test never leaks into the next.

import { afterAll, afterEach, beforeAll } from "vitest";
import { server } from "@/mocks/server";

beforeAll(() => server.listen({ onUnhandledRequest: "bypass" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
