import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { FeatureValues } from "./AdminFeaturesSettings";
import { AdminSettingsPage, type AdminSettingsPageProps } from "./AdminSettingsPage";

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
	render(<AdminSettingsPage {...props} />);
	return { props };
}

describe("AdminSettingsPage — non-integration content", () => {
	it("hides the league reset card when leagues are disabled", () => {
		setup({ features: { ...features, leaguesEnabled: false } });
		expect(screen.queryByText(/reset and recalculate leagues/i)).toBeNull();
	});

	it("shows the league reset card when leagues are enabled", () => {
		setup({ features: { ...features, leaguesEnabled: true } });
		expect(screen.getByText(/reset and recalculate leagues/i)).toBeTruthy();
	});

	// The danger zone fetches and navigates for itself, so it needs a query client and a router that
	// this page-level test deliberately does not stand up. Withholding the slug is what keeps it out:
	// if that guard regressed, this test would fail on the missing providers rather than pass quietly.
	it("leaves the danger zone out until the active workspace has resolved", () => {
		setup({ workspaceSlug: undefined });
		expect(screen.queryByRole("heading", { name: /danger zone/i })).toBeNull();
	});
});
