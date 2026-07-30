import type { UpdateWorkspaceFeaturesRequest } from "@/api/types.gen";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldGroup,
	FieldLabel,
} from "@/components/ui/field";
import { Switch } from "@/components/ui/switch";

interface FeatureDefinition {
	key: keyof UpdateWorkspaceFeaturesRequest;
	label: string;
	description: string;
}

const FEATURES = [
	{
		key: "practicesEnabled",
		label: "Practice reviews",
		description: "Run AI practice reviews for contributors.",
	},
	{
		key: "mentorEnabled",
		label: "Mentor chat",
		description: "Give workspace members access to the AI mentor.",
	},
	{
		key: "achievementsEnabled",
		label: "Achievements",
		description: "Show badges and skill trees.",
	},
	{
		key: "leaderboardEnabled",
		label: "Leaderboard",
		description: "Rank contributors by their activity scores.",
	},
	{
		key: "progressionEnabled",
		label: "XP and level progression",
		description: "Show XP progress and levels on contributor profiles.",
	},
	{
		key: "leaguesEnabled",
		label: "Leagues",
		description: "Show league tiers on leaderboards and contributor profiles.",
	},
] as const satisfies ReadonlyArray<FeatureDefinition>;

export type FeatureKey = (typeof FEATURES)[number]["key"];
export type FeatureValues = Record<FeatureKey, boolean>;

export interface AdminFeaturesSettingsProps {
	values: FeatureValues;
	isSaving: boolean;
	onToggle: (feature: FeatureKey, enabled: boolean) => void;
}

export function AdminFeaturesSettings({ values, isSaving, onToggle }: AdminFeaturesSettingsProps) {
	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>Features</h2>
				</CardTitle>
				<CardDescription>Choose which capabilities are active in this workspace.</CardDescription>
			</CardHeader>
			<CardContent>
				<FieldGroup>
					{FEATURES.map(({ key, label, description }) => (
						<Field key={key} orientation="horizontal">
							<FieldContent>
								<FieldLabel htmlFor={key}>{label}</FieldLabel>
								<FieldDescription>{description}</FieldDescription>
							</FieldContent>
							<Switch
								id={key}
								checked={values[key]}
								onCheckedChange={(checked) => onToggle(key, checked)}
								disabled={isSaving}
							/>
						</Field>
					))}
				</FieldGroup>
			</CardContent>
		</Card>
	);
}
