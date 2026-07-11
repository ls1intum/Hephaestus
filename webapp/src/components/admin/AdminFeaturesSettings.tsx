import type { UpdateWorkspaceFeaturesRequest } from "@/api/types.gen";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";

// Boolean feature toggles only. `healthVisibility` is a string enum handled by its own control.
export type FeatureKey = Exclude<keyof UpdateWorkspaceFeaturesRequest, "healthVisibility">;
export type FeatureValues = Record<FeatureKey, boolean>;

export type HealthVisibility = NonNullable<UpdateWorkspaceFeaturesRequest["healthVisibility"]>;

export interface AdminFeaturesSettingsProps {
	values: FeatureValues;
	healthVisibility: HealthVisibility;
	isSaving: boolean;
	onToggle: (feature: FeatureKey, enabled: boolean) => void;
	onHealthVisibilityChange: (visibility: HealthVisibility) => void;
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
		label: "Practice Review",
		description:
			"Enable agent-based practice review for contributors. Admins and owners also get an audited practice overview.",
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
];

const VISIBILITY_OPTIONS: ReadonlyArray<{
	value: HealthVisibility;
	label: string;
	description: string;
}> = [
	{
		value: "MENTORS_ONLY",
		label: "Admins/owners only",
		description:
			"Only workspace admins and owners see the anonymised workspace insights. Developers always see their own feedback.",
	},
	{
		value: "EVERYONE",
		label: "Everyone in the workspace",
		description:
			"Every workspace member can also see the anonymised workspace insights (never any per-person data).",
	},
];

export function AdminFeaturesSettings({
	values,
	healthVisibility,
	isSaving,
	onToggle,
	onHealthVisibilityChange,
}: AdminFeaturesSettingsProps) {
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

			<div>
				<h2 className="text-lg font-semibold mb-4">Workspace health visibility</h2>
				<Card>
					<CardContent>
						<div className="space-y-4">
							<p className="text-sm text-muted-foreground">
								Anonymised workspace insights visible to: admins/owners only, or everyone in the
								workspace. This is never a score or a ranking. It only controls the workspace health
								view. The roster and per-developer drill-downs stay admin-only, and developers
								always see their own feedback.
							</p>
							<RadioGroup
								value={healthVisibility}
								onValueChange={(value) => {
									if (value) onHealthVisibilityChange(value as HealthVisibility);
								}}
								aria-label="Workspace health visibility"
							>
								{VISIBILITY_OPTIONS.map((option) => (
									<label
										key={option.value}
										htmlFor={`health-visibility-${option.value}`}
										className="flex cursor-pointer items-start gap-3 rounded-md border p-3 has-data-checked:border-primary"
									>
										<RadioGroupItem
											id={`health-visibility-${option.value}`}
											value={option.value}
											disabled={isSaving}
											className="mt-0.5"
										/>
										<div className="space-y-0.5">
											<span className="text-sm font-medium">{option.label}</span>
											<p className="text-sm text-muted-foreground">{option.description}</p>
										</div>
									</label>
								))}
							</RadioGroup>
						</div>
					</CardContent>
				</Card>
			</div>
		</div>
	);
}
