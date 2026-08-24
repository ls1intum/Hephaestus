import { ruleTester } from "../rule-tester.ts";
import { noRedundantInTheDocument } from "./no-redundant-in-the-document.ts";

ruleTester.run("no-redundant-in-the-document", noRedundantInTheDocument, {
	valid: [
		// `queryBy*` returns null instead of throwing, so here the matcher is the assertion.
		"expect(canvas.queryByRole('alert')).toBeTruthy();",
		// `.not` puts the subject one level deeper, so the matcher's object is not the `expect` call.
		"expect(canvas.getByRole('button')).not.toBeInTheDocument();",
		"expect(canvas.getByRole('row').parentElement).toBeTruthy();",
		"expect(canvas.getByRole('row').closest('tr')).toBeTruthy();",
		"expect(canvas.getByRole('button')).toBeVisible();",
		// Some other function that happens to be spelled `expect`.
		"vi.expect(canvas.getByRole('button')).toBeTruthy();",
		"expect().toBeTruthy();",
		// A matcher named by an expression names whatever that evaluates to, which is unreadable here.
		"expect(canvas.getByRole('button'))[matcher]();",
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
		{
			// `findBy*` rejects when nothing matches, so awaiting it is already the assertion.
			code: "expect(await screen.findByRole('status')).toBeInTheDocument();",
			errors: [{ messageId: "vacuous", line: 1, column: 14, endColumn: 41 }],
		},
		{
			code: "expect(canvas.getAllByRole('row')).toBeTruthy();",
			errors: [{ messageId: "vacuous" }],
		},
		{
			code: "expect(await canvas.findAllByRole('row')).toBeDefined();",
			errors: [{ messageId: "vacuous" }],
		},
		{
			// A matcher named by a literal names exactly that matcher.
			code: "expect(canvas.getByRole('button'))['toBeTruthy']();",
			errors: [{ messageId: "vacuous" }],
		},
	],
});
