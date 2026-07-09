import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import type { SlackMonitoredChannel } from "@/api/types.gen";
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

const staleChannel: SlackMonitoredChannel = {
	id: 1,
	slackTeamId: "T0000000000",
	slackChannelId: "C01STALE001",
	channelName: "old-channel",
	consentState: "ACTIVE",
	optedOutMemberCount: 0,
	createdAt: new Date("2026-01-01T00:00:00Z"),
};

function setup(overrides: Partial<AdminSettingsPageProps> = {}) {
	const props: AdminSettingsPageProps = {
		repositories: [],
		isLoadingRepositories: false,
		repositoriesError: null,
		addRepositoryError: null,
		isAddingRepository: false,
		isRemovingRepository: false,
		isResettingLeagues: false,
		onAddRepository: vi.fn(),
		onRemoveRepository: vi.fn(),
		onResetLeagues: vi.fn(),
		features,
		isSavingFeatures: false,
		onToggleFeature: vi.fn(),
		workspaceSlug: "demo",
		hasSlackConnection: false,
		slackNotificationsEnabled: false,
		onSlackSaved: vi.fn(),
		slackChannels: [],
		slackChannelCandidates: [],
		isLoadingSlackChannels: false,
		onRegisterSlackChannel: vi.fn(),
		onUpdateSlackChannelConsent: vi.fn(),
		onRemoveSlackChannel: vi.fn(),
		...overrides,
	};
	renderWithClient(<AdminSettingsPage {...props} />);
	return { props };
}

describe("AdminSettingsPage — Slack integration structure", () => {
	it("hides channel monitoring when Slack is disconnected, even if stale channels are present", () => {
		setup({ hasSlackConnection: false, slackChannels: [staleChannel] });

		expect(screen.getByRole("heading", { name: /slack integration/i })).toBeTruthy();
		expect(screen.queryByText(/slack channel monitoring/i)).toBeNull();
		expect(screen.queryByText(/old-channel/i)).toBeNull();
		expect(screen.queryByRole("heading", { name: /slack notifications/i })).toBeNull();
	});

	it("shows connected Slack management under one integration section", () => {
		setup({ hasSlackConnection: true, slackChannels: [staleChannel] });

		expect(screen.getByRole("heading", { name: /slack integration/i })).toBeTruthy();
		expect(screen.getByRole("heading", { name: /weekly digest/i })).toBeTruthy();
		expect(screen.getByRole("heading", { name: /slack channel monitoring/i })).toBeTruthy();
		expect(screen.queryByRole("heading", { name: /slack notifications/i })).toBeNull();
	});
});
