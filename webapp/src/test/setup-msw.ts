// Global Vitest setup: stand up the MSW Node server so RTL tests of the query-driven auth
// components hit deterministic mocked endpoints (the same handlers Storybook uses).
//
// `onUnhandledRequest: "bypass"` keeps tests that issue no network calls unaffected — only
// the explicitly-mocked auth endpoints are intercepted. Handlers reset after each test so a
// `server.use(...)` override in one test never leaks into the next.

import { afterAll, afterEach, beforeAll } from "vitest";
import { client } from "@/api/client.gen";
import { server } from "@/mocks/server";

// The generated hey-api client resolves request paths against `baseUrl` (it does
// `new URL(path, baseUrl)`); with no baseUrl a relative path like `/user` throws
// "Failed to parse URL". In the app `client.setConfig` runs in `main.tsx`, which the
// test bundle never imports — so configure an absolute base here. The MSW handlers use
// `*/path` wildcards, so this host is matched regardless of its exact value.
client.setConfig({ baseUrl: "http://localhost:8080" });

// jsdom has no ResizeObserver; cmdk's <Command.List> observes its own size to size the popup.
// A no-op stub is enough for tests — no assertions depend on the observed measurements.
if (typeof globalThis.ResizeObserver === "undefined") {
	globalThis.ResizeObserver = class ResizeObserver {
		observe() {}
		unobserve() {}
		disconnect() {}
	};
}

// jsdom has no scrollIntoView either; cmdk calls it to keep the highlighted option in view.
if (typeof Element.prototype.scrollIntoView !== "function") {
	Element.prototype.scrollIntoView = () => {};
}

beforeAll(() => server.listen({ onUnhandledRequest: "bypass" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
