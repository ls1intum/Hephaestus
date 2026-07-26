import { expect, screen, userEvent, within } from "storybook/test";

/**
 * Drives the amount field of a budget/cap dialog with a value the form must refuse, and asserts the
 * refusal is the one the reader can act on.
 *
 * Both dialogs — the shared-model budget and the workspace's provider cap — wrap the same
 * `BudgetAmountDialog`, so each rejection is one rule stated once and three or four illustrations of
 * it. The illustrations stay separate stories because each is a different thing on screen and worth
 * its own snapshot; only the steps to reach them are shared.
 *
 * Two assertions, and the second is the one that matters: the button stays enabled precisely so
 * pressing it *explains* the rejection, and a form that silently swallowed the submit would satisfy
 * "nothing was sent" just as well as one that surfaced the reason.
 */
export async function expectAmountRejected(options: {
	/** Matches the field's label — "Monthly budget (USD)" or "Monthly cap (USD)". */
	fieldLabel: RegExp;
	/** Matches the submit button — "Save budget" or "Save cap". */
	submitLabel: RegExp;
	typed: string;
	/** The explanation the field must show, not merely some error. */
	reason: RegExp;
	onSubmit: unknown;
}) {
	const dialog = within(await screen.findByRole("dialog"));
	const input = dialog.getByLabelText(options.fieldLabel);
	await userEvent.clear(input);
	if (options.typed !== "") {
		await userEvent.type(input, options.typed);
	}
	await userEvent.click(dialog.getByRole("button", { name: options.submitLabel }));

	await expect(dialog.getByRole("alert")).toHaveTextContent(options.reason);
	await expect(options.onSubmit).not.toHaveBeenCalled();
}
