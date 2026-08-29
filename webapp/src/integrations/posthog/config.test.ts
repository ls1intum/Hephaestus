import { describe, expect, it } from "vitest";

import { posthogOptions } from "./config";

describe("the PostHog provider options", () => {
	// Left unset — or simplified to `true` — `capture_performance` defers to the PostHog project's
	// remote config, which would put request-URL capture behind a dashboard toggle instead of behind
	// the consent decision the provider is mounted on.
	it("states performance capture rather than deferring it to the project's remote config", () => {
		expect(posthogOptions.capture_performance).toStrictEqual({
			web_vitals: true,
			network_timing: false,
		});
	});
});
