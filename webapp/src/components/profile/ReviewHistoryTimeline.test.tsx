import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { observationOutcome } from "@/components/profile/observation-outcome";
import {
	type ReviewedArtifact,
	type ReviewFinding,
	ReviewHistoryTimeline,
} from "@/components/profile/ReviewHistoryTimeline";

const artifacts: ReviewedArtifact[] = [
	{
		artifactType: "PULL_REQUEST",
		artifactId: 902,
		provider: "GITHUB",
		number: 902,
		title: "Split the practice catalog loader per workspace",
		repositoryName: "HephaestusTest/practice-validation",
		url: "https://github.com/HephaestusTest/practice-validation/pull/902",
		runs: [
			{
				reviewId: "review-1",
				reviewedAt: "2026-08-12T10:26:00Z",
				findings: [
					{
						observationId: "observation-1",
						feedbackId: "feedback-1",
						practiceSlug: "records-decisions",
						practiceName: "Record significant decisions and the reasoning",
						presence: "PRESENT",
						assessment: "GOOD",
						reasoning: "The pull request explains why the loader is split by workspace.",
						evidence: "PracticeCatalogLoader.java:48–76",
						guidance: "Keep recording decisions close to the change.",
						recurrenceKey: "records-decisions",
					},
				],
			},
			{
				reviewId: "review-0",
				reviewedAt: "2026-08-09T10:26:00Z",
				findings: [
					{
						observationId: "observation-0",
						practiceSlug: "records-decisions",
						practiceName: "Record significant decisions and the reasoning",
						presence: "ABSENT",
						assessment: "BAD",
						reasoning: "The earlier revision did not record the decision.",
						guidance: "Add the reason for the change.",
						recurrenceKey: "records-decisions",
					},
				],
			},
		],
	},
];

function outcomeFor(presence: ReviewFinding["presence"], assessment?: ReviewFinding["assessment"]) {
	return observationOutcome({ presence, assessment });
}

describe("ReviewHistoryTimeline", () => {
	it.each([
		["PRESENT", "GOOD", "PRESENT_GOOD"],
		["ABSENT", "GOOD", "ABSENT_GOOD"],
		["PRESENT", "BAD", "PRESENT_BAD"],
		["ABSENT", "BAD", "ABSENT_BAD"],
		["NOT_APPLICABLE", undefined, "NOT_APPLICABLE"],
	] as const)("derives %s + %s as %s", (presence, assessment, expected) => {
		expect(outcomeFor(presence, assessment)).toBe(expected);
	});

	it("opens the reasoning, evidence, and next step from the practice row", () => {
		render(<ReviewHistoryTimeline artifacts={artifacts} />);

		expect(screen.queryByText("Why this was noted")).toBeNull();
		expect(screen.queryByText("2 strengths · 1 focus area")).toBeNull();
		expect(screen.queryByText("Current evidence")).toBeNull();
		screen.getByText("Strength shown");
		screen.getByText("Improved since 9 Aug");

		fireEvent.click(
			screen.getAllByRole("button", {
				name: /Record significant decisions and the reasoning/i,
			})[0],
		);

		screen.getByText("Why this was noted");
		screen.getByText("PracticeCatalogLoader.java:48–76");
		screen.getByText("Keep recording decisions close to the change.");
	});

	it("rates delivered feedback for a positive finding without changing its outcome", () => {
		const onRateFeedback = vi.fn();
		render(<ReviewHistoryTimeline artifacts={artifacts} onRateFeedback={onRateFeedback} />);

		fireEvent.click(
			screen.getAllByRole("button", {
				name: /Record significant decisions and the reasoning/i,
			})[0],
		);

		screen.getByText("Strength shown");
		screen.getByText("Was this feedback helpful?");
		fireEvent.click(screen.getByRole("button", { name: "Helpful" }));
		expect(onRateFeedback).toHaveBeenCalledWith("feedback-1", true);
	});

	it("keeps a dense review moment compact until more findings are requested", () => {
		const baseFinding = artifacts[0].runs[0].findings[0];
		const denseArtifact: ReviewedArtifact = {
			...artifacts[0],
			runs: [
				{
					...artifacts[0].runs[0],
					findings: Array.from({ length: 5 }, (_, index) => ({
						...baseFinding,
						observationId: `dense-${index}`,
						practiceSlug: `practice-${index}`,
						practiceName: `Practice ${index + 1}`,
					})),
				},
			],
		};

		render(<ReviewHistoryTimeline artifacts={[denseArtifact]} />);

		screen.getByText("Practice 3");
		expect(screen.queryByText("Practice 4")).toBeNull();

		fireEvent.click(screen.getByRole("button", { name: "Show more (2)" }));

		screen.getByText("Practice 4");
		screen.getByText("Practice 5");
		fireEvent.click(screen.getByRole("button", { name: "Show less" }));
		expect(screen.queryByText("Practice 4")).toBeNull();
	});
});
