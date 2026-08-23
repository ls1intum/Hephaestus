import { ruleTester } from "../rule-tester.ts";
import { noWithinCanvasElement } from "./no-within-canvas-element.ts";

ruleTester.run("no-within-canvas-element", noWithinCanvasElement, {
	valid: [
		"within(canvas.getByRole('group'));",
		"canvasElement.querySelector('[data-slot=table]');",
		"within(someOtherRoot);",
		"within(row.parentElement);",
		// A method of the same name on some other object is not Testing Library's `within`.
		"foo.within(canvasElement);",
		"render(canvasElement);",
		"within();",
	],
	invalid: [
		{
			code: "within(canvasElement);",
			errors: [{ messageId: "redundant", line: 1, column: 8, endColumn: 21 }],
		},
		{
			// `play: async (context) => …` keeps the element on the context rather than destructuring it.
			code: "within(context.canvasElement);",
			errors: [{ messageId: "redundant", line: 1, column: 8, endColumn: 29 }],
		},
	],
});
