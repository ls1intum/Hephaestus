import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { PracticeReportCard } from "@/api/types.gen";
import { PracticeReflectionCard } from "./PracticeReflectionCard";

function makePractice(overrides: Partial<PracticeReportCard> = {}): PracticeReportCard {
	return {
		name: "Clear PR description",
		areaName: "Pull requests",
		slug: "clear-pr-description",
		standing: "MIXED",
		whyItMatters: "Reviewers get oriented faster.",
		strengths: [
			{
				artifactId: 42,
				artifactType: "PULL_REQUEST",
				observationId: "s1",
				title: "Explained the user impact",
				guidance: "Good opener.",
				locator: "PR #42",
				severity: "INFO",
			},
		],
		toWorkOn: [
			{
				artifactId: 42,
				artifactType: "PULL_REQUEST",
				observationId: "w1",
				title: "Link the closing issue",
				guidance: "Add Closes #17.",
				locator: "PR #42",
				severity: "MAJOR",
			},
		],
		...overrides,
	};
}

describe("PracticeReflectionCard", () => {
	it("renders the practice name, area and why-it-matters", () => {
		render(<PracticeReflectionCard practice={makePractice()} />);
		expect(screen.getByRole("heading", { name: "Clear PR description" })).toBeTruthy();
		expect(screen.getByText("Pull requests")).toBeTruthy();
		expect(screen.getByText(/Reviewers get oriented faster\./)).toBeTruthy();
	});

	it("renders strengths and to-work-on items", () => {
		render(<PracticeReflectionCard practice={makePractice()} />);
		expect(screen.getByText("Explained the user impact")).toBeTruthy();
		expect(screen.getByText("Link the closing issue")).toBeTruthy();
		expect(screen.getByText("Add Closes #17.")).toBeTruthy();
	});

	it("shows the standing chip label", () => {
		render(<PracticeReflectionCard practice={makePractice({ standing: "STRENGTH" })} />);
		expect(screen.getByText("Strength")).toBeTruthy();
	});

	it("renders a friendly message when there is nothing to work on", () => {
		render(<PracticeReflectionCard practice={makePractice({ toWorkOn: [] })} />);
		expect(screen.getByText(/Nothing to work on right now\./)).toBeTruthy();
	});
});
