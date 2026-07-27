import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { userEvent } from "storybook/test";
import { describe, expect, it, vi } from "vitest";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "./ConfirmDialog";

interface Row {
	id: number;
	displayName: string;
}

const row: Row = { id: 7, displayName: "GPT-5" };

/** The caller's half: a row to act on, held in the caller's state exactly as every real one is. */
function Harness({ onConfirm }: { onConfirm: (subject: Row) => void }) {
	const [deleting, setDeleting] = useState<Row | null>(null);
	return (
		<>
			<Button onClick={() => setDeleting(row)}>Delete GPT-5</Button>
			<ConfirmDialog
				subject={deleting}
				onClose={() => setDeleting(null)}
				title={(subject) => `Delete “${subject.displayName}”?`}
				description="This cannot be undone."
				confirmLabel="Delete"
				onConfirm={onConfirm}
			/>
		</>
	);
}

/**
 * The popup outlives its own close by an exit animation, so "gone" is asserted rather than read off
 * the next line — `queryByRole` finds a closing dialog for one more tick.
 */
async function expectDismissed() {
	await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
}

function openConfirm(onConfirm = vi.fn()) {
	render(<Harness onConfirm={onConfirm} />);
	fireEvent.click(screen.getByRole("button", { name: "Delete GPT-5" }));
	expect(screen.getByRole("alertdialog").textContent).toContain("Delete “GPT-5”?");
	return onConfirm;
}

describe("ConfirmDialog", () => {
	it("closes as it confirms, so the request cannot be sent a second time", async () => {
		// `AlertDialogAction` is a plain Button, not the primitive's Close, so nothing closes this
		// unless the dialog does. Miss that and the row vanishes behind a modal still offering an
		// enabled Delete: a second click sends a second DELETE, which comes back 404 — an error toast
		// for a delete that worked.
		const onConfirm = openConfirm();

		fireEvent.click(screen.getByRole("button", { name: "Delete" }));

		expect(onConfirm).toHaveBeenCalledExactlyOnceWith(row);
		await expectDismissed();
	});

	it("lets Escape out while the confirmed request is still in flight", async () => {
		// WCAG 2.2 SC 2.1.2, per ADR 0027: nothing here is disabled and nothing here reads the
		// caller's pending state, so the keyboard trap has no state to live in.
		const onConfirm = vi.fn();
		render(<Harness onConfirm={onConfirm} />);
		fireEvent.click(screen.getByRole("button", { name: "Delete GPT-5" }));

		expect(screen.getByRole("button", { name: "Cancel" }).hasAttribute("disabled")).toBe(false);
		expect(screen.getByRole("button", { name: "Delete" }).hasAttribute("disabled")).toBe(false);

		await userEvent.keyboard("{Escape}");

		await expectDismissed();
		expect(onConfirm).not.toHaveBeenCalled();
	});

	it("dismisses on Cancel without acting", async () => {
		const onConfirm = openConfirm();

		fireEvent.click(screen.getByRole("button", { name: "Cancel" }));

		expect(onConfirm).not.toHaveBeenCalled();
		await expectDismissed();
	});

	it("takes the verb and its opposite from the caller", () => {
		// "Cancel" is not the opposite of every destructive verb: refusing to turn a connection off is
		// keeping it active, and a confirm whose two buttons don't oppose each other is a coin toss.
		render(
			<ConfirmDialog
				subject={row}
				onClose={vi.fn()}
				title={(subject) => `Turn off “${subject.displayName}”?`}
				description={(subject) => `Everything on ${subject.displayName} stops.`}
				confirmLabel="Turn off connection"
				cancelLabel="Keep active"
				onConfirm={vi.fn()}
			/>,
		);

		const confirm = screen.getByRole("alertdialog");
		expect(confirm.textContent).toContain("Turn off “GPT-5”?");
		expect(confirm.textContent).toContain("Everything on GPT-5 stops.");
		expect(screen.getByRole("button", { name: "Keep active" })).toBeTruthy();
		expect(screen.queryByRole("button", { name: "Cancel" })).toBeNull();
	});
});
