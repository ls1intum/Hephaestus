import { afterEach, describe, expect, it } from "vitest";

import { inertBackground } from "./inert-background";

describe("inertBackground", () => {
	afterEach(() => {
		document.body.replaceChildren();
	});

	it("makes every branch of the page but the popup's inert, and lifts it again", () => {
		const app = document.createElement("div");
		const toasts = document.createElement("section");
		const portal = document.createElement("div");
		const popup = document.createElement("div");
		portal.append(popup);
		document.body.append(app, toasts, portal);

		const lift = inertBackground(popup);

		expect(app.inert).toBe(true);
		expect(toasts.inert).toBe(true);
		expect(portal.inert).not.toBe(true);
		lift?.();
		expect(app.inert).toBe(false);
		expect(toasts.inert).toBe(false);
	});

	it("leaves a branch inert that something else made inert", () => {
		const app = document.createElement("div");
		app.inert = true;
		const portal = document.createElement("div");
		const popup = document.createElement("div");
		portal.append(popup);
		document.body.append(app, portal);

		inertBackground(popup)?.();

		expect(app.inert).toBe(true);
	});

	it("does nothing on unmount", () => {
		expect(inertBackground(null)).toBeUndefined();
	});
});
