import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { mockPracticeEvidenceAuthoring } from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";
import { PracticeEvidenceEditor, practiceEvidenceError } from "./PracticeEvidenceEditor";

const options = mockPracticeEvidenceAuthoring.artifacts[0];

describe("PracticeEvidenceEditor", () => {
	it("moves a source from optional context to required evidence", async () => {
		const user = userEvent.setup();
		const onChange = vi.fn();
		await renderWithRouter(
			<PracticeEvidenceEditor options={options} value={options.baseline} onChange={onChange} />,
			"/admin/practices/new",
		);

		await user.click(screen.getByRole("button", { name: "Customize evidence" }));
		const usageSelectors = screen.getAllByRole("combobox", { name: "Use in this practice" });
		await user.click(usageSelectors[2]);
		await user.click(await screen.findByRole("option", { name: "Required" }));

		expect(onChange).toHaveBeenCalledWith(
			expect.objectContaining({
				required: expect.arrayContaining([
					expect.objectContaining({ sourceKind: "scm.pull-request.comments" }),
				]),
				optional: [],
			}),
		);
	});

	it("rejects an evidence rule with no required source", () => {
		expect(practiceEvidenceError({ ...options.baseline, required: [] })).toBe(
			"Choose at least one required evidence source.",
		);
	});

	it("does not require integration evidence for a human-only practice", () => {
		expect(
			practiceEvidenceError({
				...options.baseline,
				detectorCapability: { assessmentMethod: "NONE", evidenceCoverage: "NONE" },
				required: [],
				optional: [],
			}),
		).toBeUndefined();
	});

	it("explains when an operator must enable required evidence", async () => {
		const conversation = mockPracticeEvidenceAuthoring.artifacts[2];
		await renderWithRouter(
			<PracticeEvidenceEditor
				options={conversation}
				value={conversation.baseline}
				onChange={vi.fn()}
			/>,
			"/admin/practices/new",
		);

		expect(screen.getByText("Required evidence is not enabled")).toBeTruthy();
		expect(screen.getByText(/An instance operator must enable Slack thread/)).toBeTruthy();
	});

	it("scopes detectability to Hephaestus rather than human observers", async () => {
		await renderWithRouter(
			<PracticeEvidenceEditor options={options} value={options.baseline} onChange={vi.fn()} />,
			"/admin/practices/new",
		);

		expect(screen.getByText(/This describes Hephaestus, not people/)).toBeTruthy();
		expect(screen.getByText(/practitioner, a peer, or a human mentor/)).toBeTruthy();
	});

	it("removes detector evidence when the practice is human-only", async () => {
		const user = userEvent.setup();
		const onChange = vi.fn();
		await renderWithRouter(
			<PracticeEvidenceEditor options={options} value={options.baseline} onChange={onChange} />,
			"/admin/practices/new",
		);

		await user.click(screen.getByRole("button", { name: "Customize evidence" }));
		await user.click(
			screen.getByRole("combobox", { name: "How can Hephaestus assess this practice?" }),
		);
		await user.click(await screen.findByRole("option", { name: "Hephaestus cannot judge it" }));

		expect(onChange).toHaveBeenLastCalledWith(
			expect.objectContaining({
				detectorCapability: { assessmentMethod: "NONE", evidenceCoverage: "NONE" },
				required: [],
				optional: [],
			}),
		);
	});
});
