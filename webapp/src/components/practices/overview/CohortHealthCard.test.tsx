import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { CohortPracticeStatus } from "@/api/types.gen";
import { CohortHealthCard } from "./CohortHealthCard";

describe("CohortHealthCard", () => {
	it("renders the k-anonymity message and hides counts when suppressed", () => {
		const health: CohortPracticeStatus = {
			name: "Clear PR description",
			slug: "clear-pr",
			suppressed: true,
		};
		render(<CohortHealthCard health={health} />);
		expect(screen.getByText(/Not enough recent activity to show this safely\./i)).toBeTruthy();
		// No standing labels/counts are rendered in the suppressed state.
		expect(screen.queryByText("Strength")).toBeNull();
		expect(screen.queryByText("Developing")).toBeNull();
	});

	it("renders the aggregate counts when not suppressed", () => {
		const health: CohortPracticeStatus = {
			name: "Clear PR description",
			slug: "clear-pr",
			strengthCount: 4,
			developingCount: 2,
			mixedCount: 1,
			noActivityCount: 3,
		};
		render(<CohortHealthCard health={health} />);
		expect(screen.getByText("Strength")).toBeTruthy();
		expect(screen.getByText("4")).toBeTruthy();
		expect(screen.getByText("2")).toBeTruthy();
	});
});
