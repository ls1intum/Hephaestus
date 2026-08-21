import { ruleTester } from "../rule-tester.ts";
import { noRedundantInTheDocument } from "./no-redundant-in-the-document.ts";

ruleTester.run("no-redundant-in-the-document", noRedundantInTheDocument, {
	valid: [
		"expect(screen.queryByRole('alert')).not.toBeInTheDocument();",
		// `.not` puts the subject one level deeper, so the matcher's object is not the `expect` call.
		"expect(canvas.getByRole('button')).not.toBeInTheDocument();",
		"expect(await screen.findByRole('status')).toBeInTheDocument();",
		"expect(canvas.getByRole('row').parentElement).toBeTruthy();",
		"expect(canvas.getByRole('row').closest('tr')).toBeTruthy();",
		"expect(canvas.getByRole('button')).toBeVisible();",
		"await expect(canvas.getByRole('button'));",
	],
	invalid: [
		{
			code: "expect(canvas.getByRole('button')).toBeInTheDocument();",
			errors: [{ messageId: "vacuous", line: 1, column: 8, endColumn: 34 }],
		},
		{ code: "expect(getByText('Save')).toBeTruthy();", errors: [{ messageId: "vacuous" }] },
		{ code: "expect(getByText('Save')).toBeDefined();", errors: [{ messageId: "vacuous" }] },
		{
			code: "expect(within(row).getByLabelText('Name')).toBeInTheDocument();",
			errors: [{ messageId: "vacuous" }],
		},
	],
});
