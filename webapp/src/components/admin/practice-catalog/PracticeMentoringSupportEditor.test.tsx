import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import type { PracticeAutomatedReviewPolicy } from "@/api/types.gen";
import { mockPracticeDefinitionOptions } from "@/mocks/fixtures/practice";
import { renderWithRouter } from "@/test/router-harness";
import {
	PracticeMentoringSupportEditor,
	practicePolicyError,
} from "./PracticeMentoringSupportEditor";

const options = mockPracticeDefinitionOptions.workTypes[0];
const recommended = options.recommendedPolicy;

function Controlled({ initial = recommended }: { initial?: PracticeAutomatedReviewPolicy }) {
	const [value, setValue] = useState(initial);
	return (
		<>
			<output data-testid="policy">
				{value.automatedReview.mode}:{value.automatedReview.evidenceSufficiency}
			</output>
			<output data-testid="reason">{value.insufficiencyReason ? "set" : "none"}</output>
			<output data-testid="limitation-count">{value.knownLimitations.length}</output>
			<PracticeMentoringSupportEditor
				value={value}
				recommended={recommended}
				supportedAutomatedReviewModes={options.supportedAutomatedReviewModes}
				onChange={setValue}
			/>
		</>
	);
}

describe("PracticeMentoringSupportEditor", () => {
	it("maps the human-facing support choices to conservative review rules", async () => {
		const user = userEvent.setup();
		await renderWithRouter(<Controlled />, "/admin/practices/new");

		await user.click(screen.getByRole("radio", { name: /Human review needed/ }));
		expect(screen.getByTestId("policy").textContent).toBe(
			"LANGUAGE_MODEL:DECLARED_EVIDENCE_INSUFFICIENT",
		);

		await user.click(screen.getByRole("radio", { name: /Guidance only/ }));
		expect(screen.getByTestId("policy").textContent).toBe("NONE:NONE");
		// With no review to constrain, there is nothing for a limitation to be a limitation of.
		expect(screen.queryByRole("button", { name: "Add limitation" })).toBeNull();
	});

	it("does not leave an empty human-review reason after returning to AI support", async () => {
		const user = userEvent.setup();
		await renderWithRouter(
			<Controlled initial={{ ...recommended, knownLimitations: [] }} />,
			"/admin/practices/new",
		);

		await user.click(screen.getByRole("radio", { name: /Human review needed/ }));
		expect(screen.getByRole("textbox", { name: /Why is human review needed/ })).toBeTruthy();
		expect(screen.getByTestId("reason").textContent).toBe("set");
		// The reason is not a limitation, so asking for a human adds nothing to that list.
		expect(screen.getByTestId("limitation-count").textContent).toBe("0");

		await user.click(screen.getByRole("radio", { name: /AI-supported mentoring/ }));
		expect(screen.queryByRole("textbox", { name: /Why is human review needed/ })).toBeNull();
		expect(screen.getByTestId("reason").textContent).toBe("none");
	});

	it("restores the limitations an author wrote after a detour through guidance only", async () => {
		const user = userEvent.setup();
		await renderWithRouter(
			<Controlled
				initial={{
					...recommended,
					knownLimitations: [
						{ code: "CUSTOM_LIMITATION", description: "Keep this edited limitation." },
					],
				}}
			/>,
			"/admin/practices/new",
		);

		await user.click(screen.getByRole("radio", { name: /Guidance only/ }));
		await user.click(screen.getByRole("radio", { name: /AI-supported mentoring/ }));

		expect(screen.getByDisplayValue("Keep this edited limitation.")).toBeTruthy();
	});

	it("derives a stable limitation code from the text so retyping it is not a rule change", async () => {
		const user = userEvent.setup();
		function CodeProbe() {
			const [value, setValue] = useState<PracticeAutomatedReviewPolicy>({
				...recommended,
				knownLimitations: [{ code: "LIMITATION_79DBDE7E", description: "" }],
			});
			return (
				<>
					<output data-testid="code">{value.knownLimitations[0]?.code}</output>
					<PracticeMentoringSupportEditor
						value={value}
						recommended={recommended}
						supportedAutomatedReviewModes={options.supportedAutomatedReviewModes}
						onChange={setValue}
					/>
				</>
			);
		}
		await renderWithRouter(<CodeProbe />, "/admin/practices/new");

		await user.type(screen.getByLabelText(/Description/), "Runtime behavior not observed");

		// A pure function of the text: identical wording always yields this code, so retyping the same
		// limitation cannot look like a review-rule change to the policy digest.
		expect(screen.getByTestId("code").textContent).toBe("RUNTIME_BEHAVIOR_NOT_OBSERVED");
	});

	it("still allows human review when AI support is unavailable", async () => {
		await renderWithRouter(
			<PracticeMentoringSupportEditor
				value={recommended}
				recommended={recommended}
				supportedAutomatedReviewModes={[]}
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

	it("preserves focus while editing limitations", async () => {
		const user = userEvent.setup();
		await renderWithRouter(<Controlled />, "/admin/practices/new");

		await user.click(screen.getByRole("button", { name: "Add limitation" }));
		const added = screen.getByRole("textbox", { name: "Description for limitation 2" });
		await waitFor(() => expect(document.activeElement).toBe(added));
		await user.type(added, "A stable limitation");
		await user.click(screen.getByRole("button", { name: "Remove limitation 2" }));
		await waitFor(() =>
			expect(document.activeElement).toBe(
				screen.getByRole("textbox", { name: "Description for limitation 1" }),
			),
		);
	});

	it("rejects a review frame the server would refuse", () => {
		expect(
			practicePolicyError({
				...recommended,
				automatedReview: {
					mode: "LANGUAGE_MODEL",
					evidenceSufficiency: "DECLARED_EVIDENCE_INSUFFICIENT",
				},
				knownLimitations: [],
			}),
		).toBe("Explain at least one limitation that requires additional context.");
		expect(
			practicePolicyError({ ...recommended, knownLimitations: [{ code: "X", description: "a" }] }),
		).toBe("Limitation identifiers must use 3–64 uppercase letters, numbers, and underscores.");
		// Evidence is no longer this function's business; it belongs to the occasion that reads it.
		expect(practicePolicyError(recommended)).toBeUndefined();
	});
});
