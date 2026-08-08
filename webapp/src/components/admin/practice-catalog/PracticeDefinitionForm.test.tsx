import { Link } from "@tanstack/react-router";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";
import { PracticeDefinitionForm, type PracticeDefinitionValue } from "./PracticeDefinitionForm";

vi.mock("@/components/shared/CodeEditor", () => ({
	CodeEditor: () => <div data-testid="code-editor" />,
}));

function renderCreateForm(onSubmit: (value: PracticeDefinitionValue) => void | Promise<void>) {
	return renderWithRouter(
		<PracticeDefinitionForm
			mode="create"
			areas={[]}
			definitionOptions={mockPracticeDefinitionOptions}
			isPending={false}
			cancelAction={<Link to="/">Cancel</Link>}
			onSubmit={onSubmit}
		/>,
		"/admin/practices/new",
	);
}

const nameField = () => screen.getByRole("textbox", { name: /Name/ });
const slugField = () => screen.getByRole("textbox", { name: "Identifier" });

async function openTechnicalSettings() {
	fireEvent.click(screen.getByRole("button", { name: /Technical settings/ }));
	return screen.findByRole("textbox", { name: "Identifier" });
}

function fillValidDraft() {
	fireEvent.change(nameField(), { target: { value: "Explain what changed and why" } });
	fireEvent.change(screen.getByRole("textbox", { name: /What to look for/ }), {
		target: { value: "Look for a description that explains the behaviour change." },
	});
}

describe("the identifier a practice is created under", () => {
	it("follows the name until an author writes one of their own", async () => {
		await renderCreateForm(vi.fn());
		await openTechnicalSettings();

		fireEvent.change(nameField(), { target: { value: "Small changes" } });
		expect((slugField() as HTMLInputElement).value).toBe("small-changes");

		// The identifier cannot be changed after the practice exists, so an author who takes it over
		// has made a decision the name is not allowed to overwrite behind them.
		fireEvent.change(slugField(), { target: { value: "reviewable-diffs" } });
		fireEvent.change(nameField(), { target: { value: "Small, reviewable changes" } });

		expect((slugField() as HTMLInputElement).value).toBe("reviewable-diffs");
		expect((nameField() as HTMLInputElement).value).toBe("Small, reviewable changes");
	});

	it("can be handed back to the name", async () => {
		await renderCreateForm(vi.fn());
		await openTechnicalSettings();

		fireEvent.change(nameField(), { target: { value: "Small changes" } });
		fireEvent.change(slugField(), { target: { value: "reviewable-diffs" } });
		fireEvent.click(screen.getByRole("button", { name: "Reset to generated identifier" }));

		expect((slugField() as HTMLInputElement).value).toBe("small-changes");

		fireEvent.change(nameField(), { target: { value: "Small, reviewable changes" } });
		expect((slugField() as HTMLInputElement).value).toBe("small-reviewable-changes");
	});
});

/**
 * `isPending` drops the instant the mutation resolves, and the caller navigates on the very next
 * line. Releasing the guard on `isPending` alone therefore races that navigation and asks "Discard
 * unsaved changes?" about the save that has just succeeded.
 */
describe("the unsaved-changes guard around a save", () => {
	it("stays out of the way of a caller navigating after a successful save", async () => {
		let settle: (() => void) | undefined;
		const saved = new Promise<void>((resolve) => {
			settle = resolve;
		});
		const { router } = await renderCreateForm(() => saved);
		fillValidDraft();

		fireEvent.click(screen.getByRole("button", { name: "Create practice" }));
		settle?.();
		await saved;
		fireEvent.click(screen.getByRole("link", { name: "Cancel" }));

		// The navigation went through rather than merely not having been interrupted yet.
		await waitFor(() => expect(router.state.location.pathname).toBe("/"));
		expect(screen.queryByRole("alertdialog")).toBeNull();
	});

	it("comes back down when the save is refused", async () => {
		let refuse: (() => void) | undefined;
		const failed = new Promise<void>((_resolve, reject) => {
			refuse = () => reject(new Error("Conflict"));
		});
		await renderCreateForm(() => failed);
		fillValidDraft();

		fireEvent.click(screen.getByRole("button", { name: "Create practice" }));
		refuse?.();
		await failed.catch(() => undefined);
		fireEvent.click(screen.getByRole("link", { name: "Cancel" }));

		// The draft is still the only copy of this practice, so leaving has to be a decision again.
		expect(
			await screen.findByRole("alertdialog", { name: "Discard unsaved changes?" }),
		).toBeTruthy();
	});

	it("is left exactly as it was by a caller that says nothing", async () => {
		// A caller returning `void` is not reporting success — holding the guard down on that would
		// leave a failed save unprotected for good.
		await renderCreateForm(vi.fn());
		fillValidDraft();

		fireEvent.click(screen.getByRole("button", { name: "Create practice" }));
		fireEvent.click(screen.getByRole("link", { name: "Cancel" }));

		expect(
			await screen.findByRole("alertdialog", { name: "Discard unsaved changes?" }),
		).toBeTruthy();
	});
});
