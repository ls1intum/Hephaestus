import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
	mockAuthorDeclaredEvidenceValidation,
	mockPracticeDefinitionOptions,
	mockPullRequestBinding,
	mockPullRequestPolicy,
} from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";
import { CuratedPracticeForm, type CuratedPracticeFormInitialValue } from "./CuratedPracticeForm";

vi.mock("@/components/shared/CodeEditor", () => ({
	CodeEditor: () => <div data-testid="code-editor" />,
}));

const initialData: CuratedPracticeFormInitialValue = {
	slug: "clear-pr-description",
	name: "Write a clear pull request description",
	bindings: [mockPullRequestBinding],
	criteria: "Review whether the description explains the change.",
	automatedReviewPolicy: mockPullRequestPolicy,
	automatedReviewValidation: mockAuthorDeclaredEvidenceValidation,
	status: {
		etag: "tag",
		state: "FROM_HEPHAESTUS",
		changeKind: "NONE",
		offered: true,
	},
};

function renderForm(overrides: Partial<CuratedPracticeFormInitialValue> = {}, onSubmit = vi.fn()) {
	return renderWithRouter(
		<CuratedPracticeForm
			mode="edit"
			areas={[]}
			definitionOptions={mockPracticeDefinitionOptions}
			initialData={{ ...initialData, ...overrides }}
			isPending={false}
			onSubmit={onSubmit}
		/>,
		"/admin/catalog/practices/clear-pr-description",
	);
}

function occasion(index: number) {
	return within(screen.getByRole("group", { name: `Starts a review when, occasion ${index}` }));
}

function signalLabel(signal: string) {
	const option = mockPracticeDefinitionOptions.workTypes[0].signals.find(
		(candidate) => candidate.signal === signal,
	);
	if (!option) throw new Error(`No signal option for ${signal}`);
	return option.displayName;
}

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
		expect(screen.queryByRole("textbox", { name: "Identifier" })).toBeNull();
	});

	it("starts a new practice on the recommended occasion rather than an empty one", async () => {
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

		// Pull requests lead the picker even though the server sorts the work types alphabetically:
		// which kind an author most often writes is presentation, and it lives in the webapp.
		expect(
			screen.getByRole("radio", { name: /Pull or merge request/ }).getAttribute("aria-checked"),
		).toBe("true");
		expect(occasion(1).getByRole("checkbox", { name: "Opened" }).getAttribute("aria-checked")).toBe(
			"true",
		);
		expect(screen.getByText("Occasion 1")).toBeTruthy();
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

	it("does not call a freshly loaded practice edited", async () => {
		await renderForm();

		// The server sorts a binding's signals and needs on the way in. Loading one and touching
		// nothing must not look like an edit, or every visit would offer to discard a draft.
		fireEvent.click(screen.getByRole("link", { name: "Cancel" }));
		await waitFor(() => expect(screen.queryByRole("alertdialog")).toBeNull());
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

	it("submits an occasion the author added, with its own evidence", async () => {
		const user = userEvent.setup();
		const onSubmit = vi.fn();
		await renderForm({}, onSubmit);

		await user.click(screen.getByRole("button", { name: "Add occasion" }));
		// The recommended moments are taken, so the second occasion starts on the first free one.
		expect(occasion(2).getByRole("checkbox", { name: "Review submitted" })).toBeTruthy();
		await user.click(occasion(2).getByRole("checkbox", { name: "Merged" }));
		await user.click(occasion(2).getByRole("checkbox", { name: "Review submitted" }));
		await user.click(screen.getByRole("button", { name: "Save changes" }));

		const submitted = onSubmit.mock.calls[0]?.[0];
		expect(submitted.bindings).toHaveLength(2);
		expect(submitted.bindings[0].signals).toEqual(mockPullRequestBinding.signals);
		expect(submitted.bindings[1].signals).toEqual(["scm.pull_request.merged"]);
	});

	it("sends focus into the occasion that is wrong, not to the top of the form", async () => {
		const user = userEvent.setup();
		const onSubmit = vi.fn();
		await renderForm({}, onSubmit);

		await user.click(screen.getByRole("button", { name: "Add occasion" }));
		for (const signal of mockPullRequestBinding.signals) {
			await user.click(occasion(1).getByRole("checkbox", { name: signalLabel(signal) }));
		}
		await user.click(screen.getByRole("button", { name: "Save changes" }));

		expect(onSubmit).not.toHaveBeenCalled();
		expect(screen.getByRole("alert").textContent).toBe(
			"Choose when this occasion starts a review.",
		);
		// With two occasions on screen, a message at the bottom of the section says nothing about which
		// one to fix; landing in it does.
		await waitFor(() => expect(document.activeElement?.id).toBe("practice-binding-0-signals"));
	});

	it("refuses to let two occasions start on the same moment", async () => {
		const user = userEvent.setup();
		await renderForm();

		await user.click(screen.getByRole("button", { name: "Add occasion" }));

		// The server rejects a signal bound twice outright, and the rejection arrives after a save. The
		// checkbox is the only place it can be explained before the author spends the round trip.
		expect(
			occasion(2)
				.getByRole("checkbox", { name: /^Opened/ })
				.getAttribute("data-disabled"),
		).not.toBeNull();
		// One hint per moment occasion 1 claims, on the moment itself rather than as a banner.
		expect(occasion(2).getAllByText(/used by another occasion/)).toHaveLength(
			mockPullRequestBinding.signals.length,
		);
	});

	it("lets one occasion say what is missing and another stay silent about it", async () => {
		const user = userEvent.setup();
		const onSubmit = vi.fn();
		await renderForm({}, onSubmit);

		await user.click(
			within(screen.getByRole("group", { name: "What this review reads, occasion 1" })).getByRole(
				"button",
				{ name: "Choose sources" },
			),
		);
		await user.click(
			within(
				screen.getByRole("radiogroup", { name: "Use Review threads in occasion 1" }),
			).getByRole("radio", { name: "Required" }),
		);
		await user.click(
			screen.getByRole("checkbox", { name: /says what is missing from Review threads/ }),
		);
		await user.click(screen.getByRole("button", { name: "Save changes" }));

		expect(onSubmit.mock.calls[0]?.[0].bindings[0].needs).toContainEqual({
			sourceKind: "scm.review-threads",
			stance: "EXHAUSTIVE",
		});
	});

	it("never offers an absence claim over a source that can never be captured whole", async () => {
		const user = userEvent.setup();
		await renderForm();

		await user.click(
			within(screen.getByRole("group", { name: "What this review reads, occasion 1" })).getByRole(
				"button",
				{ name: "Choose sources" },
			),
		);
		await user.click(
			within(screen.getByRole("radiogroup", { name: "Use Linked issues in occasion 1" })).getByRole(
				"radio",
				{ name: "Required" },
			),
		);

		// Absent rather than present-and-refused on save: the source contract can never promise a whole
		// capture of the linked issues, so no claim about what is missing from them can rest on it.
		expect(
			screen.queryByRole("checkbox", { name: /says what is missing from Linked issues/ }),
		).toBeNull();
	});

	it("strips every occasion's evidence when the practice stops being reviewed", async () => {
		const user = userEvent.setup();
		const onSubmit = vi.fn();
		await renderForm({}, onSubmit);

		await user.click(screen.getByRole("radio", { name: /Guidance only/ }));
		await user.click(screen.getByRole("button", { name: "Save changes" }));

		expect(onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({
				bindings: [expect.objectContaining({ needs: [] })],
				automatedReviewPolicy: expect.objectContaining({
					automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" },
					knownLimitations: [],
				}),
			}),
		);
		await user.click(screen.getByRole("button", { name: /Technical settings/ }));
		expect(screen.queryByText("Static analysis")).toBeNull();
	});

	it("gives every occasion its evidence back when review resumes", async () => {
		const user = userEvent.setup();
		const onSubmit = vi.fn();
		await renderForm({}, onSubmit);

		await user.click(screen.getByRole("radio", { name: /Guidance only/ }));
		await user.click(screen.getByRole("radio", { name: /AI-supported mentoring/ }));
		await user.click(screen.getByRole("button", { name: "Save changes" }));

		// Every binding must name a source the review cannot run without, so leaving guidance only has
		// to restore evidence rather than leave a practice that can never be saved.
		expect(onSubmit.mock.calls[0]?.[0].bindings[0].needs.length).toBeGreaterThan(0);
	});

	it("does not schedule mentoring when a practice needs human review", async () => {
		const user = userEvent.setup();
		const onSubmit = vi.fn();
		await renderForm({ precomputeScript: "export default {};" }, onSubmit);

		await user.click(screen.getByRole("radio", { name: /Human review needed/ }));
		await user.type(
			screen.getByRole("textbox", { name: /Why is human review needed/ }),
			"A mentor must discuss the developer's reasoning.",
		);
		await user.click(screen.getByRole("button", { name: /Technical settings/ }));
		expect(screen.queryByText("Static analysis")).toBeNull();

		await user.click(screen.getByRole("button", { name: "Save changes" }));
		const submitted = onSubmit.mock.calls[0]?.[0];
		expect(submitted?.precomputeScript).toBeUndefined();
		expect(submitted?.automatedReviewPolicy.automatedReview).toEqual({
			mode: "LANGUAGE_MODEL",
			evidenceSufficiency: "DECLARED_EVIDENCE_INSUFFICIENT",
		});
	});

	it("keeps limitation drafts when the reviewed work type changes", async () => {
		const user = userEvent.setup();
		await renderForm();

		await user.click(screen.getByRole("button", { name: "Add limitation" }));
		const limitationDescription = screen.getByRole("textbox", {
			name: "Description for limitation 2",
		});
		await user.type(limitationDescription, "Keep this limitation");
		await user.click(screen.getByRole("radio", { name: /^Issue/ }));
		await user.click(screen.getByRole("radio", { name: /Pull or merge request/ }));

		expect(screen.getByDisplayValue("Keep this limitation")).toBeTruthy();
	});

	it("keeps the mentoring choice when the reviewed work type changes", async () => {
		const user = userEvent.setup();
		await renderForm();

		await user.click(screen.getByRole("radio", { name: /Guidance only/ }));
		await user.click(screen.getByRole("radio", { name: /^Issue/ }));
		expect(screen.getByRole("radio", { name: /Guidance only/ }).getAttribute("aria-checked")).toBe(
			"true",
		);
	});

	it("keeps each work type's occasions to itself", async () => {
		const user = userEvent.setup();
		await renderForm();

		await user.click(screen.getByRole("radio", { name: /^Issue/ }));
		await user.click(occasion(1).getByRole("checkbox", { name: "Closed" }));
		await user.click(screen.getByRole("radio", { name: /Pull or merge request/ }));
		expect(occasion(1).getByRole("checkbox", { name: "Opened" }).getAttribute("aria-checked")).toBe(
			"true",
		);
		expect(occasion(1).queryByRole("checkbox", { name: "Closed" })).toBeNull();

		await user.click(screen.getByRole("radio", { name: /^Issue/ }));
		expect(occasion(1).getByRole("checkbox", { name: "Closed" }).getAttribute("aria-checked")).toBe(
			"true",
		);
	});

	it("asks about drafts only where a draft can exist", async () => {
		const user = userEvent.setup();
		await renderForm();

		expect(screen.getByRole("checkbox", { name: /Also while it is still a draft/ })).toBeTruthy();

		await user.click(screen.getByRole("radio", { name: /^Issue/ }));

		// "An issue is never a draft" — the detection gate's words. A control for a state that cannot
		// occur is worse than no control.
		expect(screen.queryByRole("checkbox", { name: /Also while it is still a draft/ })).toBeNull();
	});
});
