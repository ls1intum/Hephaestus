import type { UpdateWorkspaceFeaturesRequest } from "@/api/types.gen";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

// healthVisibility rides on the same PATCH request but is an enum tier, not a boolean toggle,
// and has no admin UI yet, so it is excluded from the switch list.
export type FeatureKey = Exclude<keyof UpdateWorkspaceFeaturesRequest, "healthVisibility">;
export type FeatureValues = Record<FeatureKey, boolean>;

export interface AdminFeaturesSettingsProps {
	values: FeatureValues;
	isSaving: boolean;
	onToggle: (feature: FeatureKey, enabled: boolean) => void;
}

interface FeatureDefinition {
	key: FeatureKey;
	label: string;
	description: string;
	children?: ReadonlyArray<{ key: FeatureKey; label: string; description: string }>;
}

const FEATURES: ReadonlyArray<FeatureDefinition> = [
	{
		key: "practicesEnabled",
		label: "Practices",
		description:
			"Practice feedback for contributors: agent reviews on their work, the My practices page, and the mentor-facing practice overview.",
		children: [
			{
				key: "practiceReviewAutoTriggerEnabled",
				label: "Auto-trigger",
				description: "Automatically trigger reviews on pull request events.",
			},
			{
				key: "practiceReviewManualTriggerEnabled",
				label: "Manual trigger",
				description: "Allow triggering reviews via the /hephaestus review bot command.",
			},
		],
	},
	{
		key: "mentorEnabled",
		label: "Mentor Chat",
		description:
			"Enable the Pi mentor chat assistant for workspace members. Requires the sandbox runtime and agent NATS to be available on the deployment.",
	},
	{
		key: "achievementsEnabled",
		label: "Achievements",
		description: "Enable the achievements system with badges and skill trees.",
	},
	{
		key: "leaderboardEnabled",
		label: "Leaderboard",
		description:
			"Show the contributor leaderboard, a legacy feature that is being retired in favor of practice feedback.",
	},
	{
		key: "progressionEnabled",
		label: "XP & Level Progression",
		description: "Show XP progress bar and level badges on profiles.",
	},
	{
		key: "leaguesEnabled",
		label: "Leagues",
		description: "Show league tiers and rankings on leaderboard and profile.",
	},
];

export function AdminFeaturesSettings({ values, isSaving, onToggle }: AdminFeaturesSettingsProps) {
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
							{FEATURES.map(({ key, label, description, children }) => (
								<div key={key}>
									<div className="flex items-center justify-between gap-4">
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
									{children && values[key] && (
										<div className="ml-6 mt-4 space-y-4 border-l-2 border-muted pl-4">
											{children.map((child) => (
												<div key={child.key} className="flex items-center justify-between gap-4">
													<div className="space-y-0.5">
														<Label htmlFor={child.key}>{child.label}</Label>
														<p className="text-sm text-muted-foreground">{child.description}</p>
													</div>
													<Switch
														id={child.key}
														checked={values[child.key]}
														onCheckedChange={(checked: boolean) => onToggle(child.key, checked)}
														disabled={isSaving}
													/>
												</div>
											))}
										</div>
									)}
								</div>
							))}
						</div>
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
