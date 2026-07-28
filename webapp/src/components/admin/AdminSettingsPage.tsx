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
}

export function AdminSettingsPage({
	isResettingLeagues,
	onResetLeagues,
	features,
	isSavingFeatures,
	onToggleFeature,
}: AdminSettingsPageProps) {
	return (
		<div className="container mx-auto py-6 max-w-4xl">
			<h1 className="text-3xl font-bold mb-8">Workspace settings</h1>

			<div className="space-y-10">
				<AdminFeaturesSettings
					values={features}
					isSaving={isSavingFeatures}
					onToggle={onToggleFeature}
				/>

				{features.leaguesEnabled && (
					<AdminLeagueSettings isResetting={isResettingLeagues} onResetLeagues={onResetLeagues} />
				)}
			</div>
		</div>
	);
}
