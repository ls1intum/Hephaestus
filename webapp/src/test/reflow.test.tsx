import { describe, expect, it } from "vitest";
import {
	expectDialogBodyScrolls,
	expectDialogFitsViewport,
	expectPageReflows,
	expectTablesScrollInPlace,
	expectTargetSize,
	expectWithinViewport,
	REFLOW_WIDTH,
} from "./reflow";

/**
 * These helpers are only ever run by the Storybook browser tier, where the story's
 * `parameters.viewport.defaultViewport = "reflow"` supplies the narrow window. Nothing in a story
 * file forces that parameter to be present, so the helpers have to be the ones that notice — this
 * suite is what stops the guard from being deleted as redundant.
 *
 * jsdom's default window is 1024 x 768, i.e. exactly the desktop width a story would silently fall
 * back to, which makes it the right harness for the negative case.
 */
describe("reflow assertions refuse to run at desktop width", () => {
	const desktop = () => {
		expect(window.innerWidth).toBeGreaterThan(REFLOW_WIDTH);
	};

	/** Passes every *other* assertion in the helpers: tiny, at the origin, inside a 1024 px window. */
	function tinyElement(): HTMLElement {
		const element = document.createElement("div");
		document.body.append(element);
		return element;
	}

	it.each([
		["expectPageReflows", () => expectPageReflows()],
		["expectWithinViewport", () => expectWithinViewport(tinyElement())],
		["expectDialogFitsViewport", () => expectDialogFitsViewport(tinyElement())],
		["expectDialogBodyScrolls", () => expectDialogBodyScrolls(tinyElement())],
		["expectTablesScrollInPlace", () => expectTablesScrollInPlace()],
	])("%s fails when the story forgot defaultViewport: reflow", async (_name, run) => {
		desktop();
		await expect(run()).rejects.toThrow(/reflow viewport/);
	});

	it("expectTargetSize still holds at any width, so it carries no viewport guard", async () => {
		desktop();
		const control = tinyElement();
		control.getBoundingClientRect = () => new DOMRect(0, 0, 32, 32);
		await expect(expectTargetSize(control)).resolves.toBeUndefined();
	});
});
