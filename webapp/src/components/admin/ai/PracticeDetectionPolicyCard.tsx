import { AlertCircle } from "lucide-react";
import type {
	AgentConfig,
	AiSettingsView,
	UpdatePracticeReviewSettings,
	UpdateWorkspaceFeaturesRequest,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldContent, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";

/** The resettable policy fields — derived from the generated client so it stays in sync. */
export type PracticeReviewField = NonNullable<UpdatePracticeReviewSettings["reset"]>[number];

const FANOUT = "__fanout__";
const COVERAGE_ALL = "all";
const COVERAGE_ROLE = "role";

const COVERAGE_ITEMS = [
	{ value: COVERAGE_ALL, label: "All contributors" },
	{ value: COVERAGE_ROLE, label: "Only users with the review role" },
];

export interface PracticeDetectionPolicyCardProps {
	settings?: AiSettingsView;
	configs: AgentConfig[];
	autoTriggerEnabled: boolean;
	manualTriggerEnabled: boolean;
	isLoading: boolean;
	isError?: boolean;
	isSaving: boolean;
	onBindConfig: (configId: number | null) => void;
	onUpdateReviewSettings: (settings: UpdatePracticeReviewSettings) => void;
	onUpdateFeatures: (features: UpdateWorkspaceFeaturesRequest) => void;
	onResetReviewField: (field: PracticeReviewField) => void;
	onRetry?: () => void;
}

export function PracticeDetectionPolicyCard({
	settings,
	configs,
	autoTriggerEnabled,
	manualTriggerEnabled,
	isLoading,
	isError = false,
	isSaving,
	onBindConfig,
	onUpdateReviewSettings,
	onUpdateFeatures,
	onResetReviewField,
	onRetry,
}: PracticeDetectionPolicyCardProps) {
	if (isError) {
		return (
			<Alert variant="destructive">
				<AlertCircle />
				<AlertTitle>Failed to load policy</AlertTitle>
				<AlertDescription>
					<p>The practice detection policy could not be loaded.</p>
					{onRetry && (
						<Button variant="outline" size="sm" className="mt-2" onClick={onRetry}>
							Retry
						</Button>
					)}
				</AlertDescription>
			</Alert>
		);
	}

	if (isLoading || !settings) {
		return (
			<div className="flex h-40 items-center justify-center">
				<Spinner className="h-6 w-6" />
			</div>
		);
	}

	const boundConfigId = settings.practiceConfigId;
	const hasBoundConfig = boundConfigId != null;
	const boundConfig = hasBoundConfig
		? configs.find((config) => config.id === boundConfigId)
		: undefined;
	const boundRuntimePaused = boundConfig?.enabled === false;
	const runtimeItems = [
		{ value: FANOUT, label: "All enabled models" },
		...configs.map((config) => ({ value: String(config.id), label: config.name })),
	];

	// Each policy knob shows whether its value is an explicit workspace override or inherited — and when
	// inherited, spells out the inherited value so a "Reset to default" is a predictable action.
	const inheritHint = (overridden: boolean, field: PracticeReviewField, inheritedValue: string) =>
		overridden ? (
			<Button
				variant="link"
				size="sm"
				className="h-auto p-0 text-xs"
				disabled={isSaving}
				onClick={() => onResetReviewField(field)}
			>
				Reset to default
			</Button>
		) : (
			<span className="text-muted-foreground text-xs">
				Inherited from default ({inheritedValue})
			</span>
		);

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader>
					<CardTitle className="text-base">AI model</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<Field>
						<FieldLabel htmlFor="practice-runtime">Model</FieldLabel>
						<Select
							items={runtimeItems}
							value={hasBoundConfig ? String(boundConfigId) : FANOUT}
							disabled={isSaving}
							onValueChange={(value) => {
								if (!value) return;
								onBindConfig(value === FANOUT ? null : Number(value));
							}}
						>
							<SelectTrigger id="practice-runtime">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value={FANOUT}>All enabled models</SelectItem>
								{configs.map((config) => (
									<SelectItem key={config.id} value={String(config.id)}>
										{config.name}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<FieldDescription>
							Use one specific model, or run every enabled model in parallel.
						</FieldDescription>
					</Field>

					{boundRuntimePaused && (
						<Alert variant="destructive">
							<AlertCircle />
							<AlertTitle>Selected model is disabled</AlertTitle>
							<AlertDescription>
								“{boundConfig?.name}” is turned off — practice reviews won't run until you re-enable
								it (on the AI models page) or pick a different model.
							</AlertDescription>
						</Alert>
					)}
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle className="text-base">Triggers</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="trigger-auto">Automatic reviews</FieldLabel>
							<FieldDescription>Run automatically when PR/MR events arrive.</FieldDescription>
						</FieldContent>
						<Switch
							id="trigger-auto"
							checked={autoTriggerEnabled}
							disabled={isSaving}
							onCheckedChange={(checked) =>
								onUpdateFeatures({ practiceReviewAutoTriggerEnabled: checked })
							}
						/>
					</Field>
					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="trigger-manual">Manual reviews</FieldLabel>
							<FieldDescription>
								Let contributors request a review with a bot command on a PR/MR.
							</FieldDescription>
						</FieldContent>
						<Switch
							id="trigger-manual"
							checked={manualTriggerEnabled}
							disabled={isSaving}
							onCheckedChange={(checked) =>
								onUpdateFeatures({ practiceReviewManualTriggerEnabled: checked })
							}
						/>
					</Field>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle className="text-base">Review policy</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="policy-skip-drafts">Skip drafts</FieldLabel>
							<FieldDescription>Don't review draft PRs/MRs.</FieldDescription>
							{inheritHint(
								settings.skipDraftsOverride != null,
								"SKIP_DRAFTS",
								settings.skipDrafts ? "on" : "off",
							)}
						</FieldContent>
						<Switch
							id="policy-skip-drafts"
							checked={settings.skipDrafts}
							disabled={isSaving}
							onCheckedChange={(checked) => onUpdateReviewSettings({ skipDrafts: checked })}
						/>
					</Field>

					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="policy-deliver-merged">Comment on merged PRs/MRs</FieldLabel>
							<FieldDescription>Post feedback even after a PR/MR is merged.</FieldDescription>
							{inheritHint(
								settings.deliverToMergedOverride != null,
								"DELIVER_TO_MERGED",
								settings.deliverToMerged ? "on" : "off",
							)}
						</FieldContent>
						<Switch
							id="policy-deliver-merged"
							checked={settings.deliverToMerged}
							disabled={isSaving}
							onCheckedChange={(checked) => onUpdateReviewSettings({ deliverToMerged: checked })}
						/>
					</Field>

					<Field>
						<FieldLabel htmlFor="policy-cooldown">Cooldown (minutes)</FieldLabel>
						{/* Uncontrolled on purpose (the lone numeric field): commit on blur to avoid a PATCH per
						    keystroke, and key-remount on the server-confirmed value to re-sync after save. */}
						<Input
							key={settings.cooldownMinutes}
							id="policy-cooldown"
							type="number"
							min={0}
							defaultValue={settings.cooldownMinutes}
							disabled={isSaving}
							onBlur={(e) => {
								const value = Number(e.target.value);
								if (Number.isFinite(value) && value !== settings.cooldownMinutes) {
									onUpdateReviewSettings({ cooldownMinutes: Math.max(0, Math.trunc(value)) });
								}
							}}
							className="w-32"
						/>
						<FieldDescription>
							Minimum minutes between reviews for the same PR/MR. 0 disables the cooldown.
						</FieldDescription>
						{inheritHint(
							settings.cooldownMinutesOverride != null,
							"COOLDOWN_MINUTES",
							`${settings.cooldownMinutes} min`,
						)}
					</Field>

					<Field>
						<FieldLabel htmlFor="policy-coverage">Who gets reviews</FieldLabel>
						<Select
							items={COVERAGE_ITEMS}
							value={settings.runForAllUsers ? COVERAGE_ALL : COVERAGE_ROLE}
							disabled={isSaving}
							onValueChange={(value) => {
								if (!value) return;
								onUpdateReviewSettings({ runForAllUsers: value === COVERAGE_ALL });
							}}
						>
							<SelectTrigger id="policy-coverage">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								<SelectItem value={COVERAGE_ALL}>All contributors</SelectItem>
								<SelectItem value={COVERAGE_ROLE}>Only users with the review role</SelectItem>
							</SelectContent>
						</Select>
						<FieldDescription>
							Review every contributor, or only those with the review role. Assigning that role
							isn't self-serve in-product yet.
						</FieldDescription>
						{inheritHint(
							settings.runForAllUsersOverride != null,
							"RUN_FOR_ALL_USERS",
							settings.runForAllUsers ? "All contributors" : "Only the review role",
						)}
					</Field>
				</CardContent>
			</Card>
		</div>
	);
}
