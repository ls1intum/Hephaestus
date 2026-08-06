import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import type { PracticeAutomatedReviewPolicy } from "@/api/types.gen";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";
import { PracticeEvidenceEditor, practiceEvidenceError } from "./PracticeEvidenceEditor";

const options = mockPracticeDefinitionOptions.workTypes[0];

describe("PracticeEvidenceEditor", () => {
	it("starts with a simple mentoring choice and progressively reveals evidence controls", async () => {
		const user = userEvent.setup();
		const onChange = vi.fn();
		await renderWithRouter(
			<PracticeEvidenceEditor
				options={options}
				value={options.recommendedRequirements}
				onChange={onChange}
			/>,
			"/admin/practices/new",
		);

		expect(
			screen.getByRole("radio", { name: /AI-supported mentoring/ }).getAttribute("aria-checked"),
		).toBe("true");
		expect(screen.queryByRole("radiogroup", { name: /Use in this practice/ })).toBeNull();
		expect(screen.queryByRole("button", { name: "Use recommended evidence" })).toBeNull();

		await user.click(screen.getByRole("button", { name: "Customize evidence" }));
		// Every choice is visible once the panel is open; none is hidden behind a menu.
		await user.click(
			within(
				screen.getByRole("radiogroup", {
					name: "Use in this practice for Inline review comments",
				}),
			).getByRole("radio", { name: "Required" }),
		);

		expect(onChange).toHaveBeenCalledWith(
			expect.objectContaining({
				requiredEvidence: expect.arrayContaining([
					expect.objectContaining({ sourceKind: "scm.pull-request.comments" }),
				]),
				optionalContext: [],
			}),
		);
	});

	it("maps the human-facing support choices to conservative review rules", async () => {
		const user = userEvent.setup();
		function ControlledEditor() {
			const [value, setValue] = useState<PracticeAutomatedReviewPolicy>(
				options.recommendedRequirements,
			);
			return (
				<>
					<output data-testid="policy">
						{value.automatedReview.mode}:{value.automatedReview.evidenceSufficiency}
					</output>
					<PracticeEvidenceEditor options={options} value={value} onChange={setValue} />
				</>
			);
		}
		await renderWithRouter(<ControlledEditor />, "/admin/practices/new");

		await user.click(screen.getByRole("radio", { name: /Human review needed/ }));
		expect(screen.getByTestId("policy").textContent).toBe(
			"LANGUAGE_MODEL:DECLARED_EVIDENCE_INSUFFICIENT",
		);
		expect(screen.getByText(/does not have enough context/)).toBeTruthy();

		await user.click(screen.getByRole("radio", { name: /Guidance only/ }));
		expect(screen.getByTestId("policy").textContent).toBe("NONE:NONE");
		expect(screen.queryByRole("button", { name: "Customize evidence" })).toBeNull();
	});

	it("does not leave an empty human-review reason after returning to AI support", async () => {
		const user = userEvent.setup();
		function ControlledEditor() {
			const [value, setValue] = useState<PracticeAutomatedReviewPolicy>({
				...options.recommendedRequirements,
				knownLimitations: [],
			});
			return (
				<>
					<output data-testid="limitation-count">{value.knownLimitations.length}</output>
					<output data-testid="reason">{value.insufficiencyReason ? "set" : "none"}</output>
					<PracticeEvidenceEditor options={options} value={value} onChange={setValue} />
				</>
			);
		}
		await renderWithRouter(<ControlledEditor />, "/admin/practices/new");

		await user.click(screen.getByRole("radio", { name: /Human review needed/ }));
		expect(screen.getByRole("textbox", { name: /Why is human review needed/ })).toBeTruthy();
		expect(screen.getByTestId("reason").textContent).toBe("set");
		// The reason is not a limitation, so asking for a human adds nothing to that list.
		expect(screen.getByTestId("limitation-count").textContent).toBe("0");

		await user.click(screen.getByRole("radio", { name: /AI-supported mentoring/ }));
		expect(screen.queryByRole("textbox", { name: /Why is human review needed/ })).toBeNull();
		expect(screen.getByTestId("reason").textContent).toBe("none");
	});

	it("restores evidence customization after switching to guidance only", async () => {
		const user = userEvent.setup();
		const edited = {
			...options.recommendedRequirements,
			knownLimitations: [
				{ code: "CUSTOM_LIMITATION", description: "Keep this edited limitation." },
			],
		};
		function ControlledEditor() {
			const [value, setValue] = useState<PracticeAutomatedReviewPolicy>(edited);
			return <PracticeEvidenceEditor options={options} value={value} onChange={setValue} />;
		}
		await renderWithRouter(<ControlledEditor />, "/admin/practices/new");

		await user.click(screen.getByRole("radio", { name: /Guidance only/ }));
		await user.click(screen.getByRole("radio", { name: /AI-supported mentoring/ }));
		await user.click(screen.getByRole("button", { name: "Customize evidence" }));

		expect(screen.getByDisplayValue("Keep this edited limitation.")).toBeTruthy();
	});

	it("derives a stable limitation code from the text so retyping it is not a rule change", async () => {
		const user = userEvent.setup();
		function ControlledEditor() {
			const [value, setValue] = useState<PracticeAutomatedReviewPolicy>({
				...options.recommendedRequirements,
				knownLimitations: [{ code: "LIMITATION_79DBDE7E", description: "" }],
			});
			return (
				<>
					<output data-testid="code">{value.knownLimitations[0]?.code}</output>
					<PracticeEvidenceEditor options={options} value={value} onChange={setValue} />
				</>
			);
		}
		await renderWithRouter(<ControlledEditor />, "/admin/practices/new");
		await user.click(screen.getByRole("button", { name: "Customize evidence" }));

		await user.type(screen.getByLabelText(/Description/), "Runtime behavior not observed");

		// A pure function of the text: identical wording always yields this code, so retyping the same
		// limitation cannot look like a review-rule change to the policy digest.
		expect(screen.getByTestId("code").textContent).toBe("RUNTIME_BEHAVIOR_NOT_OBSERVED");
	});

	it("offers conversation evidence without asking the author to arrange authorization", async () => {
		const conversation = mockPracticeDefinitionOptions.workTypes[2];
		await renderWithRouter(
			<PracticeEvidenceEditor
				options={conversation}
				value={conversation.recommendedRequirements}
				onChange={vi.fn()}
			/>,
			"/admin/practices/new",
		);

		// Connecting the integration and enabling the practice is the authorization; a practice author
		// has no lever to pull here, so an instruction to go and set one would be a dead end.
		expect(screen.queryByText(/authorization/i)).toBeNull();
		expect(screen.getByRole("button", { name: "Customize evidence" })).toBeTruthy();
	});

	it("still allows human review when AI support is unavailable", async () => {
		await renderWithRouter(
			<PracticeEvidenceEditor
				options={{ ...options, supportedAutomatedReviewModes: [] }}
				value={options.recommendedRequirements}
				onChange={vi.fn()}
			/>,
			"/admin/practices/new",
		);

		expect(
			screen.getByRole("radio", { name: /AI-supported mentoring/ }).getAttribute("data-disabled"),
		).not.toBeNull();
		expect(
			screen.getByRole("radio", { name: /Human review needed/ }).getAttribute("data-disabled"),
		).toBeNull();
	});

	it("rejects invalid evidence rules", () => {
		expect(
			practiceEvidenceError({ ...options.recommendedRequirements, requiredEvidence: [] }),
		).toBe("Choose at least one required evidence source.");
		expect(
			practiceEvidenceError({
				...options.recommendedRequirements,
				automatedReview: { mode: "NONE", evidenceSufficiency: "NONE" },
				requiredEvidence: [],
				optionalContext: [],
				knownLimitations: [],
			}),
		).toBeUndefined();
	});

	it("opens invalid evidence and preserves focus while editing limitations", async () => {
		const user = userEvent.setup();
		function ControlledEditor() {
			const [value, setValue] = useState<PracticeAutomatedReviewPolicy>({
				...options.recommendedRequirements,
				requiredEvidence: [],
			});
			return (
				<PracticeEvidenceEditor
					options={options}
					value={value}
					onChange={setValue}
					error="Choose at least one required evidence source."
				/>
			);
		}
		await renderWithRouter(<ControlledEditor />, "/admin/practices/new");
		const evidenceGroup = screen.getByRole("region", {
			name: "How Hephaestus can help",
		});
		// aria-invalid is not a global attribute and assistive tech ignores it on a region, so the
		// message itself has to be reachable: it is announced (role="alert") and the region points
		// at it.
		expect(evidenceGroup.getAttribute("aria-describedby")).toBe("practice-evidence-error");
		expect(screen.getByRole("alert").textContent).toBe(
			"Choose at least one required evidence source.",
		);

		await user.click(screen.getByRole("button", { name: "Add limitation" }));
		const addedDescription = screen.getByRole("textbox", {
			name: "Description for limitation 2",
		});
		await waitFor(() => expect(document.activeElement).toBe(addedDescription));
		await user.type(addedDescription, "A stable limitation");
		await user.click(screen.getByRole("button", { name: "Remove limitation 2" }));
		await waitFor(() =>
			expect(document.activeElement).toBe(
				screen.getByRole("textbox", { name: "Description for limitation 1" }),
			),
		);
	});
});
