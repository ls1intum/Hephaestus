import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import type { PracticeAutomatedReviewPolicy } from "@/api/types.gen";
import { mockPracticeEvidenceOptions } from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";
import { PracticeEvidenceEditor, practiceEvidenceError } from "./PracticeEvidenceEditor";

const options = mockPracticeEvidenceOptions.workTypes[0];

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
		expect(screen.queryByText("Pull request details")).toBeNull();

		await user.click(screen.getByRole("button", { name: "Customize evidence" }));
		await user.click(
			screen.getByRole("combobox", {
				name: "Use in this practice for Inline review comments",
			}),
		);
		await user.click(await screen.findByRole("option", { name: "Required" }));

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

		await user.click(screen.getByRole("radio", { name: /Human context needed/ }));
		expect(screen.getByTestId("policy").textContent).toBe(
			"LANGUAGE_MODEL:DECLARED_EVIDENCE_INSUFFICIENT",
		);
		expect(screen.getByText(/not enough for AI guidance/)).toBeTruthy();

		await user.click(screen.getByRole("radio", { name: /Practice guidance only/ }));
		expect(screen.getByTestId("policy").textContent).toBe("NONE:NONE");
		expect(screen.queryByRole("button", { name: "Customize evidence" })).toBeNull();
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

		await user.click(screen.getByRole("radio", { name: /Practice guidance only/ }));
		await user.click(screen.getByRole("radio", { name: /AI-supported mentoring/ }));
		await user.click(screen.getByRole("button", { name: "Customize evidence" }));

		expect(screen.getByDisplayValue("Keep this edited limitation.")).toBeTruthy();
	});

	it("explains when an operator must authorize required evidence", async () => {
		const conversation = mockPracticeEvidenceOptions.workTypes[2];
		await renderWithRouter(
			<PracticeEvidenceEditor
				options={conversation}
				value={conversation.recommendedRequirements}
				onChange={vi.fn()}
			/>,
			"/admin/practices/new",
		);

		expect(screen.getByText("Required evidence is not authorized")).toBeTruthy();
		expect(screen.getByText(/An instance operator must authorize Slack thread/)).toBeTruthy();
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
			name: "AI-supported practice mentoring",
		});
		expect(evidenceGroup.getAttribute("aria-invalid")).toBe("true");
		expect(evidenceGroup.getAttribute("aria-describedby")).toBe("practice-evidence-error");

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
