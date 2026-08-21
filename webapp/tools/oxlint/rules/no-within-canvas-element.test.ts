import { ruleTester } from "../rule-tester.ts";
import { noWithinCanvasElement } from "./no-within-canvas-element.ts";

ruleTester.run("no-within-canvas-element", noWithinCanvasElement, {
	valid: [
		"within(canvas.getByRole('group'));",
		"within(await screen.findByRole('dialog'));",
		"canvasElement.querySelector('[data-slot=table]');",
		"const width = canvasElement.clientWidth;",
		"within(someOtherRoot);",
	],
	invalid: [
		{
			code: "within(canvasElement);",
			// The report points at the argument, not the whole call — that is what makes the fix obvious.
			errors: [{ messageId: "redundant", line: 1, column: 8, endColumn: 21 }],
		},
	],
});
