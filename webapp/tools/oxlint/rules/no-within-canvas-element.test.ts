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
			errors: [{ messageId: "redundant", line: 1, column: 8, endColumn: 21 }],
		},
	],
});
