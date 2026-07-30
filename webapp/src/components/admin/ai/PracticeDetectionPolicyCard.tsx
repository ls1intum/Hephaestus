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
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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
import { cn } from "@/lib/utils";

export type PracticeReviewField = NonNullable<UpdatePracticeReviewSettingsRequest["reset"]>[number];
export type PracticeReviewWorkspaceUpdate = Pick<
	UpdateWorkspaceFeaturesRequest,
	"practicesEnabled" | "practiceReviewAutoTriggerEnabled" | "practiceReviewManualTriggerEnabled"
>;

const COVERAGE_ALL = "all";
const COVERAGE_ROLE = "role";

const COVERAGE_ITEMS = [
	{ value: COVERAGE_ALL, label: "All matching work" },
	{ value: COVERAGE_ROLE, label: "Work assigned to review participants" },
];

export interface PracticeDetectionPolicyCardProps {
	settings?: PracticeReviewSettings;
	detectionBinding?: AgentBinding;
	availableModels: AvailableLlmModel[];
	workspaceSlug: string;
	enabled: boolean;
	autoTriggerEnabled: boolean;
	manualTriggerEnabled: boolean;
	isLoading: boolean;
	isError?: boolean;
	error?: unknown;
	savingReviewSettings: boolean;
	savingWorkspaceSettings: boolean;
	onUpdateReviewSettings: (settings: UpdatePracticeReviewSettingsRequest) => void;
	onUpdateWorkspaceSettings: (settings: PracticeReviewWorkspaceUpdate) => void;
	onResetReviewField: (field: PracticeReviewField) => void;
	onRetry?: () => void;
}

export function PracticeDetectionPolicyCard({
	settings,
	detectionBinding,
	availableModels,
	workspaceSlug,
	enabled,
	autoTriggerEnabled,
	manualTriggerEnabled,
	isLoading,
	isError = false,
	error,
	savingReviewSettings,
	savingWorkspaceSettings,
	onUpdateReviewSettings,
	onUpdateWorkspaceSettings,
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

	const boundModelId = detectionBinding?.instanceModelId ?? detectionBinding?.workspaceModelId;
	const boundModelScope = detectionBinding?.instanceModelId != null ? "SHARED" : "WORKSPACE";
	const boundModel =
		boundModelId != null
			? availableModels.find(
					(model) => model.scope === boundModelScope && model.id === boundModelId,
				)
			: undefined;
	const detectionRunnable = detectionBinding?.ready === true && detectionBinding.enabled;

	const inheritHint = (overridden: boolean, field: PracticeReviewField, inheritedValue: string) =>
		overridden ? (
			<div className="text-left">
				<Button
					variant="link"
					size="sm"
					className="h-auto p-0 text-xs"
					disabled={savingReviewSettings}
					onClick={() => onResetReviewField(field)}
				>
					Reset to default
				</Button>
			</div>
		) : (
			<span className="self-start text-muted-foreground text-xs">
				Inherited from default ({inheritedValue})
			</span>
		);

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader>
					<CardTitle>Project review status</CardTitle>
					<CardDescription>
						Control whether connected project events and supported commands can start reviews.
					</CardDescription>
				</CardHeader>
				<CardContent>
					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="practice-reviews-enabled">Project reviews</FieldLabel>
							<FieldDescription>
								{enabled
									? detectionRunnable
										? "Connected project events and supported commands can start new reviews."
										: "Reviews are on, but cannot run until the review model is ready."
									: detectionRunnable
										? "Connected project events and supported commands won't start new reviews."
										: "Choose a runnable review model before starting reviews."}
							</FieldDescription>
						</FieldContent>
						<Switch
							id="practice-reviews-enabled"
							checked={enabled}
							disabled={savingWorkspaceSettings || (!enabled && !detectionRunnable)}
							onCheckedChange={(checked) =>
								onUpdateWorkspaceSettings({ practicesEnabled: checked })
							}
						/>
					</Field>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>Review model</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					{detectionRunnable ? (
						<Field>
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
								{detectionBinding
									? "The practice feedback model is unavailable"
									: "Practice feedback has no model"}
							</AlertTitle>
							<AlertDescription>
								<p>
									{detectionBinding
										? "The model it runs on is turned off or was removed, so reviews can't run."
										: "Reviews can't run until a model is chosen."}
								</p>
								<Link
									to="/w/$workspaceSlug/admin/models"
									params={{ workspaceSlug }}
									className={cn(buttonVariants({ variant: "outline", size: "sm" }), "mt-2")}
								>
									Open AI models
								</Link>
							</AlertDescription>
						</Alert>
					)}
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>When reviews run</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="trigger-auto">Automatic reviews</FieldLabel>
							<FieldDescription>
								Run reviews when a configured GitHub or GitLab event occurs.
							</FieldDescription>
						</FieldContent>
						<Switch
							id="trigger-auto"
							checked={autoTriggerEnabled}
							disabled={savingWorkspaceSettings || (!autoTriggerEnabled && !detectionRunnable)}
							onCheckedChange={(checked) =>
								onUpdateWorkspaceSettings({ practiceReviewAutoTriggerEnabled: checked })
							}
						/>
					</Field>
					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="trigger-manual">Manual reviews</FieldLabel>
							<FieldDescription>Allow supported bot commands to request a review.</FieldDescription>
						</FieldContent>
						<Switch
							id="trigger-manual"
							checked={manualTriggerEnabled}
							disabled={savingWorkspaceSettings || (!manualTriggerEnabled && !detectionRunnable)}
							onCheckedChange={(checked) =>
								onUpdateWorkspaceSettings({ practiceReviewManualTriggerEnabled: checked })
							}
						/>
					</Field>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>Review rules</CardTitle>
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
							disabled={savingReviewSettings}
							onCheckedChange={(checked) => onUpdateReviewSettings({ skipDrafts: checked })}
						/>
					</Field>

					<Field orientation="horizontal">
						<FieldContent>
							<FieldLabel htmlFor="policy-deliver-merged">Post feedback after merge</FieldLabel>
							{inheritHint(
								settings.deliverToMergedOverride != null,
								"DELIVER_TO_MERGED",
								settings.deliverToMerged ? "on" : "off",
							)}
						</FieldContent>
						<Switch
							id="policy-deliver-merged"
							checked={settings.deliverToMerged}
							disabled={savingReviewSettings}
							onCheckedChange={(checked) => onUpdateReviewSettings({ deliverToMerged: checked })}
						/>
					</Field>

					<Field>
						<FieldLabel htmlFor="policy-cooldown">Time between reviews (minutes)</FieldLabel>
						<Input
							key={settings.cooldownMinutes}
							id="policy-cooldown"
							type="number"
							min={0}
							defaultValue={settings.cooldownMinutes}
							disabled={savingReviewSettings}
							onBlur={(e) => {
								const value = Number(e.target.value);
								if (Number.isFinite(value) && value !== settings.cooldownMinutes) {
									onUpdateReviewSettings({ cooldownMinutes: Math.max(0, Math.trunc(value)) });
								}
							}}
							className="w-32"
						/>
						<FieldDescription>
							Minimum time between reviews of the same pull or merge request. Use 0 for no wait.
						</FieldDescription>
						{inheritHint(
							settings.cooldownMinutesOverride != null,
							"COOLDOWN_MINUTES",
							`${settings.cooldownMinutes} min`,
						)}
					</Field>

					<Field>
						<FieldLabel htmlFor="policy-coverage">Eligible work</FieldLabel>
						<Select
							items={COVERAGE_ITEMS}
							value={settings.runForAllUsers ? COVERAGE_ALL : COVERAGE_ROLE}
							disabled={savingReviewSettings}
							onValueChange={(value) => {
								if (!value) return;
								onUpdateReviewSettings({ runForAllUsers: value === COVERAGE_ALL });
							}}
						>
							<SelectTrigger id="policy-coverage">
								<SelectValue />
							</SelectTrigger>
							<SelectContent>
								{COVERAGE_ITEMS.map((item) => (
									<SelectItem key={item.value} value={item.value}>
										{item.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						{!settings.runForAllUsers && (
							<FieldDescription>
								A review starts only when at least one assignee has the review role.
							</FieldDescription>
						)}
						{inheritHint(
							settings.runForAllUsersOverride != null,
							"RUN_FOR_ALL_USERS",
							settings.runForAllUsers
								? "All matching work"
								: "Work assigned to review participants",
						)}
					</Field>
				</CardContent>
			</Card>
		</div>
	);
}
