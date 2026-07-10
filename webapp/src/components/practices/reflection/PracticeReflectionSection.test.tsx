import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import type { PracticeReportCard } from "@/api/types.gen";
import { PracticeReflectionSection } from "./PracticeReflectionSection";

function renderWithClient(node: ReactNode) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false } },
	});
	return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

const practice: PracticeReportCard = {
	name: "Clear PR description",
	slug: "clear-pr-description",
	standing: "STRENGTH",
	strengths: [
		{
			artifactId: 1,
			artifactType: "PULL_REQUEST",
			observationId: "s1",
			title: "Explained the impact",
		},
	],
	toWorkOn: [],
};

describe("PracticeReflectionSection", () => {
	it("renders the non-competitive heading and subtitle", () => {
		renderWithClient(<PracticeReflectionSection practices={[]} isLoading={false} />);
		expect(screen.getByRole("heading", { name: "My Practices" })).toBeTruthy();
		expect(screen.getByText(/for your growth, not a score/i)).toBeTruthy();
	});

	it("shows the empty state when there is no feedback", () => {
		renderWithClient(<PracticeReflectionSection practices={[]} isLoading={false} />);
		expect(screen.getByText(/No recent practice feedback yet/i)).toBeTruthy();
	});

	it("renders a reflection card per practice when populated", () => {
		renderWithClient(<PracticeReflectionSection practices={[practice]} isLoading={false} />);
		expect(screen.getByRole("heading", { name: "Clear PR description" })).toBeTruthy();
		expect(screen.getByText("Explained the impact")).toBeTruthy();
		// The copy affordance appears only when there is something to copy.
		expect(screen.getByRole("button", { name: /copy my practice summary/i })).toBeTruthy();
	});
});
