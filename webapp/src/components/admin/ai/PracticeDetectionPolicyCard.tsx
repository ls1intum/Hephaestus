import { Link } from "@tanstack/react-router";
import { AlertCircle } from "lucide-react";
import type {
	AgentBinding,
	AvailableLlmModel,
	PracticeReviewSettings,
	UpdatePracticeReviewSettingsRequest,
	UpdateWorkspaceFeaturesRequest,
} from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldLabel,
	FieldTitle,
} from "@/components/ui/field";
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
export type PracticeReviewField = NonNullable<UpdatePracticeReviewSettingsRequest["reset"]>[number];

const COVERAGE_ALL = "all";
const COVERAGE_ROLE = "role";

const COVERAGE_ITEMS = [
	{ value: COVERAGE_ALL, label: "All contributors" },
	{ value: COVERAGE_ROLE, label: "Only users with the review role" },
];

export interface PracticeDetectionPolicyCardProps {
	settings?: PracticeReviewSettings;
	/** This workspace's PRACTICE_DETECTION binding, or undefined when the purpose is unbound. */
	detectionBinding?: AgentBinding;
	availableModels: AvailableLlmModel[];
	/** Links to the AI models page, where the model binding is owned and edited. */
	workspaceSlug: string;
	autoTriggerEnabled: boolean;
	manualTriggerEnabled: boolean;
	isLoading: boolean;
	isError?: boolean;
	/** The failure behind `isError`; its ProblemDetail and status decide the wording and whether a
	 * Retry is offered at all (a 403 cannot be retried away). */
	error?: unknown;
	isSaving: boolean;
	onUpdateReviewSettings: (settings: UpdatePracticeReviewSettingsRequest) => void;
	onUpdateFeatures: (features: UpdateWorkspaceFeaturesRequest) => void;
	onResetReviewField: (field: PracticeReviewField) => void;
	onRetry?: () => void;
}

export function PracticeDetectionPolicyCard({
	settings,
	detectionBinding,
	availableModels,
	workspaceSlug,
	autoTriggerEnabled,
	manualTriggerEnabled,
	isLoading,
	isError = false,
	error,
	isSaving,
	onUpdateReviewSettings,
	onUpdateFeatures,
	onResetReviewField,
	onRetry,
}: PracticeDetectionPolicyCardProps) {
	if (isError) {
		return (
			<QueryErrorAlert error={error} title="Couldn't load the review policy" onRetry={onRetry} />
		);
	}

	if (isLoading || !settings) {
		return (
			<div className="flex h-40 items-center justify-center">
				<Spinner className="size-6" />
			</div>
		);
	}

	// The model binding is owned by the AI models page (one write path); this page only reports what
	// detection currently runs on, because every policy knob below is meaningless while it cannot run.
	const boundModelId = detectionBinding?.instanceModelId ?? detectionBinding?.workspaceModelId;
	const boundModelScope = detectionBinding?.instanceModelId != null ? "SHARED" : "WORKSPACE";
	const boundModel =
		boundModelId != null
			? availableModels.find(
					(model) => model.scope === boundModelScope && model.id === boundModelId,
				)
			: undefined;
	const detectionRunnable = detectionBinding?.ready === true && detectionBinding.enabled;

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
					<CardTitle>Model</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					{detectionRunnable ? (
						<Field>
							{/* A read-only report, not a control: `FieldTitle` names the value below it without
							    claiming a `for` target that doesn't exist. */}
							<FieldTitle>Practice detection runs on</FieldTitle>
							<p className="font-medium text-sm">
								{boundModel?.displayName ?? `Model #${boundModelId}`}
							</p>
							<FieldDescription>
								Change the model on the{" "}
								<Link
									to="/w/$workspaceSlug/admin/models"
									params={{ workspaceSlug }}
									className="underline underline-offset-4"
								>
									AI models page
								</Link>
								.
							</FieldDescription>
						</Field>
					) : (
						<Alert variant="destructive">
							<AlertCircle />
							<AlertTitle>
								{detectionBinding ? "Bound model cannot run" : "No model bound"}
							</AlertTitle>
							<AlertDescription>
								<p>
									{detectionBinding
										? "The model bound to practice detection is disabled or no longer available, so no review can run."
										: "Practice detection has no model bound, so no review can run."}
								</p>
								<Button
									variant="outline"
									size="sm"
									className="mt-2"
									// No `nativeButton={false}`: it would stamp `role="button"` over the anchor, and
									// this is a navigation — it must keep announcing itself as a link.
									render={<Link to="/w/$workspaceSlug/admin/models" params={{ workspaceSlug }} />}
								>
									Open AI models
								</Button>
							</AlertDescription>
						</Alert>
					)}
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>Triggers</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="trigger-auto">Automatic reviews</FieldLabel>
						</FieldContent>
						<Switch
							id="trigger-auto"
							checked={autoTriggerEnabled}
							disabled={isSaving || (!autoTriggerEnabled && !detectionRunnable)}
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
							disabled={isSaving || (!manualTriggerEnabled && !detectionRunnable)}
							onCheckedChange={(checked) =>
								onUpdateFeatures({ practiceReviewManualTriggerEnabled: checked })
							}
						/>
					</Field>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>Review policy</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="policy-skip-drafts">Skip drafts</FieldLabel>
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
						<FieldDescription>Assigning that role isn't self-serve yet.</FieldDescription>
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
