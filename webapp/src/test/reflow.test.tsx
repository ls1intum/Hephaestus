import { describe, expect, it } from "vitest";
import {
	expectDialogBodyScrolls,
	expectDialogFitsViewport,
	expectPageReflows,
	expectTablesScrollInPlace,
	expectWithinViewport,
	REFLOW_WIDTH,
} from "./reflow";

/** jsdom's default window is the desktop width a story that forgot the viewport falls back to. */
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
});
