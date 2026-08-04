import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestEvidence,
} from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";
import { CuratedPracticeForm, type CuratedPracticeFormInitialValue } from "./CuratedPracticeForm";

vi.mock("@/components/shared/CodeEditor", () => ({
	CodeEditor: () => <div data-testid="code-editor" />,
}));

const initialData: CuratedPracticeFormInitialValue = {
	slug: "clear-pr-description",
	name: "Write a clear pull request description",
	artifactType: "PULL_REQUEST",
	triggerEvents: ["PullRequestCreated"],
	criteria: "Review whether the description explains the change.",
	automatedReviewPolicy: mockPullRequestEvidence,
	automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	status: {
		etag: "tag",
		state: "FROM_HEPHAESTUS",
		changeKind: "NONE",
		offered: true,
	},
};

describe("CuratedPracticeForm", () => {
	it("associates validation messages with invalid fields", async () => {
		await renderWithRouter(
			<CuratedPracticeForm
				mode="create"
				areas={[]}
				definitionOptions={mockPracticeDefinitionOptions}
				isPending={false}
				onSubmit={vi.fn()}
			/>,
			"/admin/catalog/new",
		);

		fireEvent.click(screen.getByRole("button", { name: "Create practice" }));
		const name = screen.getByRole("textbox", { name: /Name/ });
		expect(name.getAttribute("aria-invalid")).toBe("true");
		expect(name.getAttribute("aria-describedby")).toBe("practice-name-error");
		expect(
			screen.getByRole("textbox", { name: /What to look for/ }).getAttribute("aria-describedby"),
		).toBe("practice-criteria-description practice-criteria-error");
		expect(screen.queryByText("Select at least one trigger event")).toBeNull();
		expect(screen.queryByRole("textbox", { name: "Identifier" })).toBeNull();
	});

	it("asks before discarding an edited draft", async () => {
		await renderWithRouter(
			<CuratedPracticeForm
				mode="create"
				areas={[]}
				definitionOptions={mockPracticeDefinitionOptions}
				isPending={false}
				onSubmit={vi.fn()}
			/>,
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
			<CuratedPracticeForm
				mode="create"
				areas={[]}
				definitionOptions={mockPracticeDefinitionOptions}
				isPending={false}
				onSubmit={vi.fn()}
			/>,
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
				definitionOptions={mockPracticeDefinitionOptions}
				initialData={initialData}
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

	it("submits edited evidence requirements", async () => {
		const user = userEvent.setup();
		const onSubmit = vi.fn();
		await renderWithRouter(
			<CuratedPracticeForm
				mode="edit"
				areas={[]}
				definitionOptions={mockPracticeDefinitionOptions}
				initialData={initialData}
				isPending={false}
				onSubmit={onSubmit}
			/>,
			"/admin/catalog/practices/clear-pr-description",
		);

		await user.click(screen.getByRole("radio", { name: /Guidance only/ }));
		await user.click(screen.getByRole("button", { name: "Save changes" }));

		expect(onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({
				triggerEvents: [],
				automatedReviewPolicy: expect.objectContaining({
					automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" },
					requiredEvidence: [],
					optionalContext: [],
					knownLimitations: [],
				}),
			}),
		);
		await user.click(screen.getByRole("button", { name: /Technical settings/ }));
		expect(screen.queryByText("Run mentoring when *")).toBeNull();
		expect(screen.queryByText("Static analysis")).toBeNull();
	});

	it("does not schedule mentoring when a practice needs human review", async () => {
		const user = userEvent.setup();
		const onSubmit = vi.fn();
		await renderWithRouter(
			<CuratedPracticeForm
				mode="edit"
				areas={[]}
				definitionOptions={mockPracticeDefinitionOptions}
				initialData={{ ...initialData, precomputeScript: "export default {};" }}
				isPending={false}
				onSubmit={onSubmit}
			/>,
			"/admin/catalog/practices/clear-pr-description",
		);

		await user.click(screen.getByRole("radio", { name: /Human review needed/ }));
		await user.type(
			screen.getByRole("textbox", { name: /Why is human review needed/ }),
			"A mentor must discuss the developer's reasoning.",
		);
		await user.click(screen.getByRole("button", { name: /Technical settings/ }));
		expect(screen.queryByText("Run mentoring when *")).toBeNull();
		expect(screen.queryByText("Static analysis")).toBeNull();

		await user.click(screen.getByRole("button", { name: "Save changes" }));
		const submitted = onSubmit.mock.calls[0]?.[0];
		expect(submitted?.precomputeScript).toBeUndefined();
		expect(onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({
				triggerEvents: [],
				automatedReviewPolicy: expect.objectContaining({
					automatedReview: {
						mode: "LANGUAGE_MODEL",
						evidenceSufficiency: "DECLARED_EVIDENCE_INSUFFICIENT",
					},
				}),
			}),
		);
	});

	it("keeps evidence drafts when the reviewed work type changes", async () => {
		const user = userEvent.setup();
		await renderWithRouter(
			<CuratedPracticeForm
				mode="edit"
				areas={[]}
				definitionOptions={mockPracticeDefinitionOptions}
				initialData={initialData}
				isPending={false}
				onSubmit={vi.fn()}
			/>,
			"/admin/catalog/practices/clear-pr-description",
		);

		await user.click(screen.getByRole("button", { name: "Customize evidence" }));
		await user.click(screen.getByRole("button", { name: "Add limitation" }));
		const limitationDescription = screen.getByRole("textbox", {
			name: "Description for limitation 2",
		});
		if (!limitationDescription) throw new Error("New limitation description was not rendered");
		await user.type(limitationDescription, "Keep this limitation");
		await user.click(screen.getByRole("combobox", { name: "Review this kind of work" }));
		await user.click(await screen.findByRole("option", { name: "Issue" }));
		await user.click(screen.getByRole("combobox", { name: "Review this kind of work" }));
		await user.click(await screen.findByRole("option", { name: /Pull or merge request/ }));

		expect(screen.getByDisplayValue("Keep this limitation")).toBeTruthy();
	});

	it("keeps the mentoring choice when the reviewed work type changes", async () => {
		const user = userEvent.setup();
		await renderWithRouter(
			<CuratedPracticeForm
				mode="edit"
				areas={[]}
				definitionOptions={mockPracticeDefinitionOptions}
				initialData={initialData}
				isPending={false}
				onSubmit={vi.fn()}
			/>,
			"/admin/catalog/practices/clear-pr-description",
		);

		await user.click(screen.getByRole("radio", { name: /Guidance only/ }));
		await user.click(screen.getByRole("combobox", { name: "Review this kind of work" }));
		await user.click(await screen.findByRole("option", { name: "Issue" }));
		expect(screen.getByRole("radio", { name: /Guidance only/ }).getAttribute("aria-checked")).toBe(
			"true",
		);

		await user.click(screen.getByRole("radio", { name: /AI-supported mentoring/ }));
		await user.click(screen.getByRole("button", { name: /Technical settings/ }));
		expect(
			screen.getByRole("checkbox", { name: "Issue is opened" }).getAttribute("aria-checked"),
		).toBe("true");
	});

	it("keeps trigger drafts separate by reviewed work type", async () => {
		const user = userEvent.setup();
		await renderWithRouter(
			<CuratedPracticeForm
				mode="edit"
				areas={[]}
				definitionOptions={mockPracticeDefinitionOptions}
				initialData={initialData}
				isPending={false}
				onSubmit={vi.fn()}
			/>,
			"/admin/catalog/practices/clear-pr-description",
		);

		await user.click(screen.getByRole("combobox", { name: "Review this kind of work" }));
		await user.click(await screen.findByRole("option", { name: "Issue" }));
		await user.click(screen.getByRole("button", { name: /Technical settings/ }));
		await user.click(screen.getByRole("checkbox", { name: "Issue is closed" }));
		await user.click(screen.getByRole("combobox", { name: "Review this kind of work" }));
		await user.click(await screen.findByRole("option", { name: /Pull or merge request/ }));
		expect(
			screen
				.getByRole("checkbox", { name: "Pull or merge request is opened" })
				.getAttribute("aria-checked"),
		).toBe("true");
		await user.click(screen.getByRole("combobox", { name: "Review this kind of work" }));
		await user.click(await screen.findByRole("option", { name: "Issue" }));
		expect(
			screen.getByRole("checkbox", { name: "Issue is closed" }).getAttribute("aria-checked"),
		).toBe("true");
	});
});
