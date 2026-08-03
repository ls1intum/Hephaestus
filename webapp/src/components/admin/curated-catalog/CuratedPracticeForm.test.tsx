import { fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPullRequestEvidence,
} from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";
import { CuratedPracticeForm } from "./CuratedPracticeForm";

vi.mock("@/components/shared/CodeEditor", () => ({
	CodeEditor: () => <div data-testid="code-editor" />,
}));

describe("CuratedPracticeForm", () => {
	it("associates validation messages with invalid fields", async () => {
		await renderWithRouter(
			<CuratedPracticeForm mode="create" areas={[]} isPending={false} onSubmit={vi.fn()} />,
			"/admin/catalog/new",
		);

		fireEvent.click(screen.getByRole("button", { name: "Create practice" }));
		const name = screen.getByRole("textbox", { name: /Name/ });
		expect(name.getAttribute("aria-invalid")).toBe("true");
		expect(name.getAttribute("aria-describedby")).toBe("practice-name-error");
		expect(
			screen.getByRole("textbox", { name: /Evaluation criteria/ }).getAttribute("aria-describedby"),
		).toBe("practice-criteria-description practice-criteria-error");
		expect(screen.getByText("Select at least one trigger event")).toBeTruthy();
	});

	it("asks before discarding an edited draft", async () => {
		await renderWithRouter(
			<CuratedPracticeForm mode="create" areas={[]} isPending={false} onSubmit={vi.fn()} />,
			"/admin/catalog/new",
		);

		fireEvent.change(screen.getByRole("textbox", { name: /Name/ }), {
			target: { value: "A new practice" },
		});
		fireEvent.click(screen.getByRole("link", { name: "Cancel" }));

		expect(
			await screen.findByRole("alertdialog", { name: "Discard unsaved changes?" }),
		).toBeTruthy();
		fireEvent.click(screen.getByRole("button", { name: "Keep editing" }));
		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
		expect(screen.getByDisplayValue("A new practice")).toBeTruthy();
	});

	it("keeps the draft when the discard prompt is dismissed with Escape", async () => {
		await renderWithRouter(
			<CuratedPracticeForm mode="create" areas={[]} isPending={false} onSubmit={vi.fn()} />,
			"/admin/catalog/new",
		);

		fireEvent.change(screen.getByRole("textbox", { name: /Name/ }), {
			target: { value: "A new practice" },
		});
		fireEvent.click(screen.getByRole("link", { name: "Cancel" }));
		await screen.findByRole("alertdialog", { name: "Discard unsaved changes?" });

		fireEvent.keyDown(document, { key: "Escape" });

		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
		expect(screen.getByDisplayValue("A new practice")).toBeTruthy();
	});

	it("preserves the draft and blocks saving after an edit conflict", async () => {
		await renderWithRouter(
			<CuratedPracticeForm
				mode="edit"
				areas={[]}
				initialData={{
					slug: "clear-pr-description",
					name: "Write a clear pull request description",
					artifactType: "PULL_REQUEST",
					triggerEvents: ["PullRequestCreated"],
					criteria: "Assess whether the description explains the change.",
					evidence: mockPullRequestEvidence,
					evidenceValidation: mockAuthorDeclaredEvidenceValidation,
					status: {
						etag: "tag",
						state: "FROM_HEPHAESTUS",
						changeKind: "NONE",
						offered: true,
					},
				}}
				isPending={false}
				conflict
				onContinueWithDraft={vi.fn()}
				onSubmit={vi.fn()}
			/>,
			"/admin/catalog/practices/clear-pr-description",
		);

		expect(screen.getByDisplayValue("Write a clear pull request description")).toBeTruthy();
		expect(
			(screen.getByRole("button", { name: "Save changes" }) as HTMLButtonElement).disabled,
		).toBe(true);
		expect(
			(screen.getByRole("button", { name: "Continue with my draft" }) as HTMLButtonElement)
				.disabled,
		).toBe(false);
	});
});
