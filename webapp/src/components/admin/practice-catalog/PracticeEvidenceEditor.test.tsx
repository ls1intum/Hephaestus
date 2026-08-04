import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import type { PracticeAutomatedAssessmentPolicy } from "@/api/types.gen";
import { mockPracticeEvidenceOptions } from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";
import { PracticeEvidenceEditor, practiceEvidenceError } from "./PracticeEvidenceEditor";

const options = mockPracticeEvidenceOptions.workTypes[0];

describe("PracticeEvidenceEditor", () => {
	it("moves a source from optional context to required evidence", async () => {
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

		await user.click(screen.getByRole("button", { name: "Edit evidence requirements" }));
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

	it("rejects evidence requirements with no required source", () => {
		expect(
			practiceEvidenceError({ ...options.recommendedRequirements, requiredEvidence: [] }),
		).toBe("Choose at least one required evidence source.");
	});

	it("does not require integration evidence for a practice without automated assessment", () => {
		expect(
			practiceEvidenceError({
				...options.recommendedRequirements,
				automatedAssessment: { mode: "NONE", evidenceSufficiency: "NONE" },
				requiredEvidence: [],
				optionalContext: [],
				knownLimitations: [],
			}),
		).toBeUndefined();
	});

	it("does not offer an assessment mode the runtime cannot execute", async () => {
		const user = userEvent.setup();
		await renderWithRouter(
			<PracticeEvidenceEditor
				options={options}
				value={options.recommendedRequirements}
				onChange={vi.fn()}
			/>,
			"/admin/practices/new",
		);

		await user.click(screen.getByRole("button", { name: "Edit evidence requirements" }));
		await user.click(
			screen.getByRole("combobox", { name: "How should Hephaestus assess reviewed work?" }),
		);

		expect(screen.queryByRole("option", { name: "Rule-based (not supported)" })).toBeNull();
	});

	it("shows plain-language labels instead of machine values", async () => {
		const user = userEvent.setup();
		await renderWithRouter(
			<PracticeEvidenceEditor
				options={options}
				value={options.recommendedRequirements}
				onChange={vi.fn()}
			/>,
			"/admin/practices/new",
		);

		await user.click(screen.getByRole("button", { name: "Edit evidence requirements" }));
		expect(
			screen.getByRole("combobox", { name: "How should Hephaestus assess reviewed work?" }),
		).toHaveProperty("textContent", expect.stringContaining("Language model"));
		expect(
			screen.getByRole("combobox", { name: "When evidence checks pass, is it enough?" }),
		).toHaveProperty("textContent", expect.stringContaining("Requirements are sufficient"));
		expect(
			screen.getByRole("combobox", { name: "Use in this practice for Pull request details" }),
		).toHaveProperty("textContent", expect.stringContaining("Required"));
		expect(
			screen.getByRole("combobox", { name: "Minimum completeness for Pull request details" }),
		).toHaveProperty("textContent", expect.stringContaining("Complete"));
		expect(
			screen.getByRole("combobox", { name: "Minimum freshness for Pull request details" }),
		).toHaveProperty("textContent", expect.stringContaining("Current"));
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

	it("separates Hephaestus assessment from human assessment", async () => {
		await renderWithRouter(
			<PracticeEvidenceEditor
				options={options}
				value={options.recommendedRequirements}
				onChange={vi.fn()}
			/>,
			"/admin/practices/new",
		);

		expect(screen.getByText(/This setting only controls Hephaestus/)).toBeTruthy();
		expect(screen.getByText(/Human assessment, if applicable/)).toBeTruthy();
	});

	it("restores edited evidence after temporarily disabling automated assessment", async () => {
		const user = userEvent.setup();
		const edited = {
			...options.recommendedRequirements,
			knownLimitations: [
				{ code: "CUSTOM_LIMITATION", description: "Keep this edited limitation." },
			],
		};
		function ControlledEditor() {
			const [value, setValue] = useState<PracticeAutomatedAssessmentPolicy>(edited);
			return <PracticeEvidenceEditor options={options} value={value} onChange={setValue} />;
		}
		await renderWithRouter(<ControlledEditor />, "/admin/practices/new");

		await user.click(screen.getByRole("button", { name: "Edit evidence requirements" }));
		await user.click(
			screen.getByRole("combobox", { name: "How should Hephaestus assess reviewed work?" }),
		);
		await user.click(await screen.findByRole("option", { name: "No automated assessment" }));

		expect(screen.queryByText("1. A practice review starts")).toBeNull();
		await user.click(
			screen.getByRole("combobox", { name: "How should Hephaestus assess reviewed work?" }),
		);
		await user.click(await screen.findByRole("option", { name: "Language model" }));

		expect(
			(screen.getByRole("textbox", { name: "Description for limitation 1" }) as HTMLInputElement)
				.value,
		).toBe("Keep this edited limitation.");
	});

	it("keeps disabled assessment drafts separate by evidence profile", async () => {
		const user = userEvent.setup();
		const pullRequest = {
			...options.recommendedRequirements,
			knownLimitations: [
				{ code: "CUSTOM_PULL_REQUEST_LIMITATION", description: "Keep pull request context." },
			],
		};
		const issue = mockPracticeEvidenceOptions.workTypes[1];
		function ProfileEditor() {
			const [selectedOptions, setSelectedOptions] = useState(options);
			const [value, setValue] = useState<PracticeAutomatedAssessmentPolicy>(pullRequest);
			return (
				<>
					<button
						type="button"
						onClick={() => {
							setSelectedOptions(issue);
							setValue(issue.recommendedRequirements);
						}}
					>
						Edit issue evidence
					</button>
					<button
						type="button"
						onClick={() => {
							setSelectedOptions(options);
							setValue({
								...options.recommendedRequirements,
								automatedAssessment: { mode: "NONE", evidenceSufficiency: "NONE" },
								requiredEvidence: [],
								optionalContext: [],
								knownLimitations: [],
							});
						}}
					>
						Return to pull request evidence
					</button>
					<PracticeEvidenceEditor options={selectedOptions} value={value} onChange={setValue} />
				</>
			);
		}
		await renderWithRouter(<ProfileEditor />, "/admin/practices/new");

		await user.click(screen.getByRole("button", { name: "Edit evidence requirements" }));
		await user.click(
			screen.getByRole("combobox", { name: "How should Hephaestus assess reviewed work?" }),
		);
		await user.click(await screen.findByRole("option", { name: "No automated assessment" }));
		await user.click(screen.getByRole("button", { name: "Edit issue evidence" }));
		await user.click(screen.getByRole("button", { name: "Return to pull request evidence" }));
		await user.click(
			screen.getByRole("combobox", { name: "How should Hephaestus assess reviewed work?" }),
		);
		await user.click(await screen.findByRole("option", { name: "Language model" }));

		expect(screen.getByDisplayValue("Keep pull request context.")).toBeTruthy();
		expect(screen.queryByDisplayValue(/implemented correctly/)).toBeNull();
	});

	it("opens invalid requirements and manages limitation focus", async () => {
		const user = userEvent.setup();
		function ControlledEditor() {
			const [value, setValue] = useState<PracticeAutomatedAssessmentPolicy>({
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
		const evidenceGroup = screen.getByRole("region", { name: "Evidence for automated assessment" });
		expect(evidenceGroup.getAttribute("aria-invalid")).toBe("true");
		expect(evidenceGroup.getAttribute("aria-describedby")).toBe("practice-evidence-error");

		expect(
			await screen.findByRole("combobox", {
				name: "Use in this practice for Pull request details",
			}),
		).toBeTruthy();
		await user.click(screen.getByRole("button", { name: "Add limitation" }));
		const addedDescription = screen.getByRole("textbox", {
			name: "Description for limitation 2",
		});
		await waitFor(() => expect(document.activeElement).toBe(addedDescription));
		await user.type(addedDescription, "A stable limitation");
		expect(document.activeElement).toBe(addedDescription);
		await user.click(screen.getByRole("button", { name: "Remove limitation 2" }));
		await waitFor(() =>
			expect(document.activeElement).toBe(
				screen.getByRole("textbox", { name: "Description for limitation 1" }),
			),
		);
	});
});
