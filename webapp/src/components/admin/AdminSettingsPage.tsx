import { AdminDangerZoneSettings } from "./AdminDangerZoneSettings";
import {
	AdminFeaturesSettings,
	type FeatureKey,
	type FeatureValues,
} from "./AdminFeaturesSettings";
import { AdminLeagueSettings } from "./AdminLeagueSettings";

export interface AdminSettingsPageProps {
	isResettingLeagues: boolean;
	onResetLeagues: () => void;
	features: FeatureValues;
	isSavingFeatures: boolean;
	onToggleFeature: (feature: FeatureKey, enabled: boolean) => void;
	workspaceSlug?: string;
}

export function AdminSettingsPage({
	isResettingLeagues,
	onResetLeagues,
	features,
	isSavingFeatures,
	onToggleFeature,
	workspaceSlug,
}: AdminSettingsPageProps) {
	return (
		<div className="max-w-4xl space-y-10">
			<AdminFeaturesSettings
				values={features}
				isSaving={isSavingFeatures}
				onToggle={onToggleFeature}
			/>

			{features.leaguesEnabled && (
				<AdminLeagueSettings isResetting={isResettingLeagues} onResetLeagues={onResetLeagues} />
			)}

			{workspaceSlug != null && <AdminDangerZoneSettings workspaceSlug={workspaceSlug} />}
		</div>
	);
}
