import { Link } from "@tanstack/react-router";
import { AlertCircle } from "lucide-react";
import { useState } from "react";
import type {
	AgentBinding,
	PracticeReviewSettings as PracticeReviewSettingsData,
	UpdatePracticeReviewSettingsRequest,
	UpdateWorkspaceFeaturesRequest,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Item, ItemActions, ItemContent, ItemDescription, ItemTitle } from "@/components/ui/item";
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
import { reviewModelRunnable } from "./review/review-readiness";

export type PracticeReviewField = NonNullable<UpdatePracticeReviewSettingsRequest["reset"]>[number];
export type PracticeReviewWorkspaceUpdate = Pick<
	UpdateWorkspaceFeaturesRequest,
	"practicesEnabled" | "practiceReviewAutoTriggerEnabled" | "practiceReviewManualTriggerEnabled"
>;

export interface PracticeReviewSettingsProps {
	workspaceSlug: string;
	model: {
		binding?: AgentBinding;
		isLoading: boolean;
		isError: boolean;
		onRetry: () => void;
	};
	workspace: {
		enabled: boolean;
		autoTriggerEnabled: boolean;
		manualTriggerEnabled: boolean;
		isSaving: boolean;
		onUpdate: (settings: PracticeReviewWorkspaceUpdate) => void;
	};
	policy: {
		settings: PracticeReviewSettingsData;
		isSaving: boolean;
		onUpdate: (settings: UpdatePracticeReviewSettingsRequest) => void;
		onReset: (field: PracticeReviewField) => void;
	};
}

const COVERAGE_ALL = "all";
const COVERAGE_ROLE = "role";
const COVERAGE_ITEMS = [
	{ value: COVERAGE_ALL, label: "All matching work" },
	{ value: COVERAGE_ROLE, label: "Assigned review participants" },
];

export function PracticeReviewSettings({
	workspaceSlug,
	model,
	workspace,
	policy,
}: PracticeReviewSettingsProps) {
	return (
		<div className="space-y-6">
			<ProjectReviewStatusCard workspaceSlug={workspaceSlug} model={model} workspace={workspace} />
			<ReviewTimingCard workspace={workspace} policy={policy} />
			<ProjectReviewRulesCard policy={policy} />
			<ReviewScopeCard policy={policy} />
		</div>
	);
}

function ProjectReviewStatusCard({
	workspaceSlug,
	model,
	workspace,
}: Pick<PracticeReviewSettingsProps, "workspaceSlug" | "model" | "workspace">) {
	const modelRunnable = reviewModelRunnable(model);
	const hasTrigger = workspace.autoTriggerEnabled || workspace.manualTriggerEnabled;

	let status: string;
	if (workspace.enabled) {
		if (model.isLoading) status = "Practice reviews are on while model readiness is being checked.";
		else if (model.isError)
			status = "Practice reviews are on, but model readiness couldn't be confirmed.";
		else if (!modelRunnable)
			status = "Practice reviews are on, but none can start until the review model is ready.";
		else if (!hasTrigger)
			status = "Practice reviews are on, but nothing can start one: both ways in are switched off.";
		else status = "Practice reviews can start when their source and review rules allow it.";
	} else if (model.isLoading || model.isError || !modelRunnable) {
		status = "Choose a runnable review model before starting reviews.";
	} else {
		status = "No new practice reviews will start. Reviews already running may finish.";
	}

	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>Practice review status</h2>
				</CardTitle>
				<CardDescription>Control whether new practice reviews can start.</CardDescription>
			</CardHeader>
			<CardContent className="space-y-4">
				<Field orientation="horizontal">
					<FieldContent>
						<FieldLabel htmlFor="practice-reviews-enabled">Start practice reviews</FieldLabel>
						<FieldDescription>{status}</FieldDescription>
					</FieldContent>
					<Switch
						id="practice-reviews-enabled"
						checked={workspace.enabled}
						disabled={
							workspace.isSaving ||
							(!workspace.enabled && (model.isLoading || model.isError || !modelRunnable))
						}
						onCheckedChange={(checked) => workspace.onUpdate({ practicesEnabled: checked })}
					/>
				</Field>
				<ModelReadiness workspaceSlug={workspaceSlug} model={model} runnable={modelRunnable} />
			</CardContent>
		</Card>
	);
}

function ModelReadiness({
	workspaceSlug,
	model,
	runnable,
}: Pick<PracticeReviewSettingsProps, "workspaceSlug" | "model"> & { runnable: boolean }) {
	if (model.isLoading) {
		return (
			<Item variant="outline" size="sm">
				<ItemContent>
					<ItemTitle>Review model</ItemTitle>
					<ItemDescription>Checking readiness…</ItemDescription>
				</ItemContent>
				<ItemActions>
					<Spinner className="size-4" />
				</ItemActions>
			</Item>
		);
	}

	if (model.isError) {
		return (
			<Alert variant="warning" role="status">
				<AlertCircle />
				<AlertTitle>Couldn't check the review model</AlertTitle>
				<AlertDescription>
					<Button variant="outline" size="sm" onClick={model.onRetry}>
						Retry
					</Button>
				</AlertDescription>
			</Alert>
		);
	}

	if (runnable) {
		return (
			<Item variant="outline" size="sm">
				<ItemContent>
					<ItemTitle>Review model</ItemTitle>
					<ItemDescription>Ready to run</ItemDescription>
				</ItemContent>
				<ItemActions>
					<Link
						to="/w/$workspaceSlug/admin/models"
						params={{ workspaceSlug }}
						className={buttonVariants({ variant: "outline", size: "sm" })}
					>
						Change
					</Link>
				</ItemActions>
			</Item>
		);
	}

	return (
		<Alert variant="warning" role="status">
			<AlertCircle />
			<AlertTitle>
				{model.binding ? "The review model is unavailable" : "No review model selected"}
			</AlertTitle>
			<AlertDescription>
				<p>
					{model.binding
						? "The selected model is turned off or was removed, so reviews can't run."
						: "Reviews can't run until a model is chosen."}
				</p>
				<Link
					to="/w/$workspaceSlug/admin/models"
					params={{ workspaceSlug }}
					className={cn(buttonVariants({ variant: "outline", size: "sm" }), "mt-2")}
				>
					Choose a review model
				</Link>
			</AlertDescription>
		</Alert>
	);
}

function ReviewTimingCard({
	workspace,
	policy,
}: Pick<PracticeReviewSettingsProps, "workspace" | "policy">) {
	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>How reviews start</h2>
				</CardTitle>
				<CardDescription>
					Two ways in: the work itself reaches a moment a practice watches for, or somebody asks.
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-4">
				<Field orientation="horizontal">
					<FieldContent>
						<FieldLabel htmlFor="trigger-auto">Reviews the work starts</FieldLabel>
						<FieldDescription>
							Connected work reaching one of the moments a practice watches for — opened, merged,
							published — starts a review on its own.
						</FieldDescription>
					</FieldContent>
					<Switch
						id="trigger-auto"
						checked={workspace.autoTriggerEnabled}
						disabled={workspace.isSaving}
						onCheckedChange={(checked) =>
							workspace.onUpdate({ practiceReviewAutoTriggerEnabled: checked })
						}
					/>
				</Field>
				<Field orientation="horizontal">
					<FieldContent>
						{/* One switch, four doors — and only GitLab publishes the comment command, so the copy
						    scopes it rather than promising it to every workspace. */}
						<FieldLabel htmlFor="trigger-manual">Reviews somebody asks for</FieldLabel>
						<FieldDescription>
							The <strong>Review this now</strong> button, a backfill of past work, a recurring
							check, and <code>/hephaestus review</code> in a GitLab merge request comment. Turning
							this off stops every one of them.
						</FieldDescription>
					</FieldContent>
					<Switch
						id="trigger-manual"
						checked={workspace.manualTriggerEnabled}
						disabled={workspace.isSaving}
						onCheckedChange={(checked) =>
							workspace.onUpdate({ practiceReviewManualTriggerEnabled: checked })
						}
					/>
				</Field>
				<CooldownField
					key={policy.settings.cooldownMinutes}
					value={policy.settings.cooldownMinutes}
					overridden={policy.settings.cooldownMinutesOverride != null}
					policy={policy}
				/>
			</CardContent>
		</Card>
	);
}

function CooldownField({
	value,
	overridden,
	policy,
}: {
	value: number;
	overridden: boolean;
	policy: PracticeReviewSettingsProps["policy"];
}) {
	const [draft, setDraft] = useState(String(value));
	const parsed = Number(draft);
	const invalid = draft.trim() === "" || !Number.isInteger(parsed) || parsed < 0 || parsed > 1440;

	return (
		<Field data-invalid={invalid || undefined}>
			<FieldLabel htmlFor="policy-cooldown">Time between reviews (minutes)</FieldLabel>
			<Input
				id="policy-cooldown"
				type="number"
				min={0}
				max={1440}
				step={1}
				value={draft}
				aria-invalid={invalid || undefined}
				aria-describedby={invalid ? "policy-cooldown-error" : undefined}
				disabled={policy.isSaving}
				onChange={(event) => setDraft(event.currentTarget.value)}
				onBlur={() => {
					if (!invalid && parsed !== value) policy.onUpdate({ cooldownMinutes: parsed });
				}}
				className="max-w-32"
			/>
			<FieldDescription>
				Minimum time between reviews of the same pull or merge request, from 0 to 1,440 minutes.
			</FieldDescription>
			{invalid && (
				<FieldError id="policy-cooldown-error">Enter a whole number from 0 to 1,440.</FieldError>
			)}
			<InheritedSettingHint
				label="Time between reviews"
				overridden={overridden}
				field="COOLDOWN_MINUTES"
				inheritedValue={`${value} min`}
				policy={policy}
			/>
		</Field>
	);
}

function ProjectReviewRulesCard({ policy }: Pick<PracticeReviewSettingsProps, "policy">) {
	const settings = policy.settings;
	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>Project review rules</h2>
				</CardTitle>
			</CardHeader>
			<CardContent className="space-y-4">
				<Field orientation="horizontal">
					<FieldContent>
						<FieldLabel htmlFor="policy-deliver-merged">Post feedback after merge</FieldLabel>
						<InheritedSettingHint
							label="Post feedback after merge"
							overridden={settings.deliverToMergedOverride != null}
							field="DELIVER_TO_MERGED"
							inheritedValue={settings.deliverToMerged ? "On" : "Off"}
							policy={policy}
						/>
					</FieldContent>
					<Switch
						id="policy-deliver-merged"
						checked={settings.deliverToMerged}
						disabled={policy.isSaving}
						onCheckedChange={(checked) => policy.onUpdate({ deliverToMerged: checked })}
					/>
				</Field>

				<Field>
					<FieldLabel htmlFor="policy-coverage">Eligible work</FieldLabel>
					<Select
						items={COVERAGE_ITEMS}
						value={settings.runForAllUsers ? COVERAGE_ALL : COVERAGE_ROLE}
						disabled={policy.isSaving}
						onValueChange={(value) => {
							if (value) policy.onUpdate({ runForAllUsers: value === COVERAGE_ALL });
						}}
					>
						<SelectTrigger id="policy-coverage" className="max-w-full">
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
							A review starts only when an assignee has the review role. This role is managed by
							Hephaestus.
						</FieldDescription>
					)}
					<InheritedSettingHint
						label="Eligible work"
						overridden={settings.runForAllUsersOverride != null}
						field="RUN_FOR_ALL_USERS"
						inheritedValue={
							settings.runForAllUsers ? "All matching work" : "Assigned review participants"
						}
						policy={policy}
					/>
				</Field>
			</CardContent>
		</Card>
	);
}

/**
 * Matches are exact: a wildcard language here would be a promise the gate cannot keep, since it
 * holds the pull request row and not the diff.
 */
function ReviewScopeCard({ policy }: Pick<PracticeReviewSettingsProps, "policy">) {
	const scope = policy.settings.reviewScope;
	const targetBranches = scope?.targetBranches ?? [];
	const repositories = scope?.repositories ?? [];
	const restricted = targetBranches.length > 0 || repositories.length > 0;

	const update = (next: { targetBranches?: string[]; repositories?: string[] }) =>
		policy.onUpdate({ reviewScope: { targetBranches, repositories, ...next } });

	return (
		<Card>
			<CardHeader>
				<CardTitle>
					<h2>Review scope</h2>
				</CardTitle>
				<CardDescription>
					Narrow reviews to particular target branches or repositories. Leave both empty to review
					everything this workspace monitors.
				</CardDescription>
			</CardHeader>
			<CardContent className="space-y-6">
				<ScopeList
					id="scope-branches"
					label="Target branches"
					description="A pull request is reviewed only if it targets one of these. Issues are unaffected. Exact names — no wildcards."
					placeholder="main"
					values={targetBranches}
					disabled={policy.isSaving}
					onChange={(next) => update({ targetBranches: next })}
				/>
				<ScopeList
					id="scope-repositories"
					label="Repositories"
					description="Only work in these repositories is reviewed. Use the full owner/name."
					placeholder="acme/widgets"
					values={repositories}
					disabled={policy.isSaving}
					onChange={(next) => update({ repositories: next })}
				/>
				{restricted ? (
					<Button
						variant="link"
						size="sm"
						className="h-auto p-0 text-xs"
						disabled={policy.isSaving}
						onClick={() => policy.onReset("REVIEW_SCOPE")}
					>
						{/* The visible words open the accessible name rather than being replaced by an
						    `aria-label`, so a voice-control user can say what they read (WCAG 2.2 SC 2.5.3). */}
						Review everything again
						<span className="sr-only"> — use the default for Review scope</span>
					</Button>
				) : (
					<span className="text-muted-foreground text-xs">
						Default: every repository and every target branch
					</span>
				)}
			</CardContent>
		</Card>
	);
}

function ScopeList({
	id,
	label,
	description,
	placeholder,
	values,
	disabled,
	onChange,
}: {
	id: string;
	label: string;
	description: string;
	placeholder: string;
	values: string[];
	disabled: boolean;
	onChange: (next: string[]) => void;
}) {
	const [draft, setDraft] = useState("");
	const trimmed = draft.trim();
	const duplicate = trimmed.length > 0 && values.includes(trimmed);
	const descriptionId = `${id}-description`;
	const errorId = `${id}-error`;

	const add = () => {
		if (trimmed.length === 0 || duplicate) return;
		onChange([...values, trimmed]);
		setDraft("");
	};

	return (
		<Field data-invalid={duplicate || undefined}>
			<FieldLabel htmlFor={id}>{label}</FieldLabel>
			<FieldDescription id={descriptionId}>{description}</FieldDescription>
			<div className="flex gap-2">
				<Input
					id={id}
					value={draft}
					placeholder={placeholder}
					disabled={disabled}
					aria-invalid={duplicate || undefined}
					aria-describedby={duplicate ? `${descriptionId} ${errorId}` : descriptionId}
					onChange={(event) => setDraft(event.target.value)}
					onKeyDown={(event) => {
						if (event.key === "Enter") {
							event.preventDefault();
							add();
						}
					}}
				/>
				<Button
					variant="outline"
					disabled={disabled || trimmed.length === 0 || duplicate}
					onClick={add}
				>
					{/* Two lists, so two buttons that would otherwise both answer to "Add"; the visible word
					    still opens the name a voice-control user says (WCAG 2.2 SC 2.5.3). */}
					Add
					<span className="sr-only"> to {label.toLowerCase()}</span>
				</Button>
			</div>
			{/* The live region is mounted empty: a region inserted together with its message is not
			    reliably announced, and the only other sign of a duplicate is Add greying out. */}
			<div aria-live="polite" aria-atomic="true">
				{duplicate ? (
					<p id={errorId} className="font-normal text-destructive text-sm">
						{trimmed} is already listed.
					</p>
				) : null}
			</div>
			{values.length > 0 ? (
				<div className="space-y-2">
					{values.map((value) => (
						<Item key={value} variant="outline" size="sm">
							<ItemContent>
								<ItemTitle className="font-mono">{value}</ItemTitle>
							</ItemContent>
							<ItemActions>
								<Button
									variant="ghost"
									size="sm"
									aria-label={`Remove ${value}`}
									disabled={disabled}
									onClick={() => onChange(values.filter((entry) => entry !== value))}
								>
									Remove
								</Button>
							</ItemActions>
						</Item>
					))}
				</div>
			) : null}
		</Field>
	);
}

function InheritedSettingHint({
	label,
	overridden,
	field,
	inheritedValue,
	policy,
}: {
	label: string;
	overridden: boolean;
	field: PracticeReviewField;
	inheritedValue: string;
	policy: PracticeReviewSettingsProps["policy"];
}) {
	if (!overridden) {
		return (
			<span className="self-start text-muted-foreground text-xs">Default: {inheritedValue}</span>
		);
	}

	return (
		<div className="text-left">
			<Button
				variant="link"
				size="sm"
				className="h-auto p-0 text-xs"
				aria-label={`Use default for ${label}`}
				disabled={policy.isSaving}
				onClick={() => policy.onReset(field)}
			>
				Use default
			</Button>
		</div>
	);
}
