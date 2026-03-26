import type { UpdateWorkspaceFeaturesRequest } from "@/api/types.gen";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

export type FeatureKey = keyof UpdateWorkspaceFeaturesRequest;

export interface AdminFeaturesSettingsProps {
	practicesEnabled: boolean;
	achievementsEnabled: boolean;
	leaderboardEnabled: boolean;
	progressionEnabled: boolean;
	isSaving: boolean;
	onToggle: (feature: FeatureKey, enabled: boolean) => void;
}

const FEATURES: ReadonlyArray<{ key: FeatureKey; label: string; description: string }> = [
	{
		key: "practicesEnabled",
		label: "Best Practices",
		description: "Enable best practice detection and tracking for contributors.",
	},
	{
		key: "achievementsEnabled",
		label: "Achievements",
		description: "Enable the achievements system with badges and skill trees.",
	},
	{
		key: "leaderboardEnabled",
		label: "Leaderboard",
		description: "Enable the leaderboard ranking contributors by their activity scores.",
	},
	{
		key: "progressionEnabled",
		label: "Progression & Leagues",
		description: "Enable league points, league tiers, and progression tracking.",
	},
];

export function AdminFeaturesSettings({
	practicesEnabled,
	achievementsEnabled,
	leaderboardEnabled,
	progressionEnabled,
	isSaving,
	onToggle,
}: AdminFeaturesSettingsProps) {
	const values: Record<FeatureKey, boolean> = {
		practicesEnabled,
		achievementsEnabled,
		leaderboardEnabled,
		progressionEnabled,
	};

	return (
		<div className="space-y-6">
			<div>
				<h2 className="text-lg font-semibold mb-4">Features</h2>
				<Card>
					<CardContent>
						<div className="space-y-6">
							<p className="text-sm text-muted-foreground">
								Enable or disable workspace features. Disabled features will be hidden from the
								sidebar and inaccessible to members.
							</p>
							{FEATURES.map(({ key, label, description }) => (
								<div key={key} className="flex items-center justify-between gap-4">
									<div className="space-y-0.5">
										<Label htmlFor={key}>{label}</Label>
										<p className="text-sm text-muted-foreground">{description}</p>
									</div>
									<Switch
										id={key}
										checked={values[key]}
										onCheckedChange={(checked: boolean) => onToggle(key, checked)}
										disabled={isSaving}
									/>
								</div>
							))}
						</div>
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
