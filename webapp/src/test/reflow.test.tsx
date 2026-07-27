import { describe, expect, it } from "vitest";
import {
	expectDialogBodyScrolls,
	expectDialogFitsViewport,
	expectPageReflows,
	expectTablesScrollInPlace,
	expectWithinViewport,
	REFLOW_WIDTH,
} from "./reflow";

/**
 * Nothing in a story file forces `parameters.viewport.defaultViewport = "reflow"` to be present, so
 * the helpers have to notice its absence themselves. jsdom's default window is the desktop width a
 * story would silently fall back to, which makes it the harness for that negative case.
 */
describe("reflow assertions refuse to run at desktop width", () => {
	const desktop = () => {
		expect(window.innerWidth).toBeGreaterThan(REFLOW_WIDTH);
	};

	/** Passes every *other* assertion in the helpers, so only the viewport guard can fail below. */
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

	// `expectTargetSize` is absent from the table on purpose: it carries no viewport guard, so there
	// is nothing here for it to fail.
});
