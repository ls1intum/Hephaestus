import { AlertCircle, Info } from "lucide-react";
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
	{ value: COVERAGE_ROLE, label: "Opted-in role only" },
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
	const runtimeItems = [
		{ value: FANOUT, label: "All enabled (fan-out)" },
		...configs.map((config) => ({ value: String(config.id), label: config.name })),
	];

	// Each policy knob shows whether its value is an explicit workspace override or inherited from
	// the fleet default, with a one-click reset back to inherit when overridden.
	const inheritHint = (overridden: boolean, field: PracticeReviewField) =>
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
			<span className="text-muted-foreground text-xs">Inherited from default</span>
		);

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader>
					<CardTitle className="text-base">Practice runtime</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<Field>
						<FieldLabel htmlFor="practice-runtime">Runtime</FieldLabel>
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
								<SelectItem value={FANOUT}>All enabled (fan-out)</SelectItem>
								{configs.map((config) => (
									<SelectItem key={config.id} value={String(config.id)}>
										{config.name}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<FieldDescription>
							Bind a single runtime, or fan out to every enabled runtime.
						</FieldDescription>
					</Field>

					{!hasBoundConfig && (
						<Alert>
							<Info />
							<AlertTitle>No runtime bound</AlertTitle>
							<AlertDescription>Reviews fan out to all enabled runtimes.</AlertDescription>
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
							<FieldDescription>Allow on-demand reviews via bot command.</FieldDescription>
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
							{inheritHint(settings.skipDraftsOverride != null, "SKIP_DRAFTS")}
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
							<FieldLabel htmlFor="policy-deliver-merged">Deliver to merged</FieldLabel>
							<FieldDescription>Post feedback even after a PR/MR is merged.</FieldDescription>
							{inheritHint(settings.deliverToMergedOverride != null, "DELIVER_TO_MERGED")}
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
						{inheritHint(settings.cooldownMinutesOverride != null, "COOLDOWN_MINUTES")}
					</Field>

					<Field>
						<FieldLabel htmlFor="policy-coverage">Run for</FieldLabel>
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
								<SelectItem value={COVERAGE_ROLE}>Opted-in role only</SelectItem>
							</SelectContent>
						</Select>
						<FieldDescription>
							Review coverage: run for everyone, or only contributors with the opt-in role.
						</FieldDescription>
						{inheritHint(settings.runForAllUsersOverride != null, "RUN_FOR_ALL_USERS")}
					</Field>
				</CardContent>
			</Card>
		</div>
	);
}
