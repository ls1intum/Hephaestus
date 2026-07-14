import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import type { FeatureValues } from "./AdminFeaturesSettings";
import { AdminSettingsPage, type AdminSettingsPageProps } from "./AdminSettingsPage";

function renderWithClient(node: ReactNode) {
	const queryClient = new QueryClient({
		defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
	});
	return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

const features: FeatureValues = {
	practicesEnabled: false,
	mentorEnabled: false,
	achievementsEnabled: false,
	leaderboardEnabled: false,
	progressionEnabled: false,
	leaguesEnabled: false,
	practiceReviewAutoTriggerEnabled: true,
	practiceReviewManualTriggerEnabled: true,
};

function setup(overrides: Partial<AdminSettingsPageProps> = {}) {
	const props: AdminSettingsPageProps = {
		isResettingLeagues: false,
		onResetLeagues: vi.fn(),
		features,
		isSavingFeatures: false,
		onToggleFeature: vi.fn(),
		...overrides,
	};
	renderWithClient(<AdminSettingsPage {...props} />);
	return { props };
}

describe("AdminSettingsPage — non-integration content", () => {
	it("renders the features section", () => {
		setup();
		expect(screen.getByRole("heading", { name: /^features$/i })).toBeTruthy();
	});

	it("hides the league reset card when leagues are disabled", () => {
		setup({ features: { ...features, leaguesEnabled: false } });
		expect(screen.queryByText(/reset and recalculate leagues/i)).toBeNull();
	});

	it("shows the league reset card when leagues are enabled", () => {
		setup({ features: { ...features, leaguesEnabled: true } });
		expect(screen.getByText(/reset and recalculate leagues/i)).toBeTruthy();
	});
});
