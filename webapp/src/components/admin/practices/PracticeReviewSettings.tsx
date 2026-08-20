import { Link } from "@tanstack/react-router";
import { AlertCircle } from "lucide-react";
import { useState } from "react";
import type {
	AgentBinding,
	PracticeReviewCoveragePreview,
	PracticeReviewSettings as PracticeReviewSettingsData,
	UpdatePracticeReviewSettingsRequest,
	UpdateWorkspaceFeaturesRequest,
	WorkspaceReviewScope,
} from "@/api/types.gen";
import { FacetMultiSelect, type FacetSource } from "@/components/common/FacetMultiSelect";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button, buttonVariants } from "@/components/ui/button";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Item, ItemActions, ItemContent, ItemTitle } from "@/components/ui/item";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
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
	coverage: {
		preview: {
			data?: PracticeReviewCoveragePreview;
			isPending: boolean;
			isError: boolean;
			onPreview: (scope: WorkspaceReviewScope) => void;
		};
		repositories: FacetSource;
		people: FacetSource<{ value: number; label: string; description?: string }>;
	};
}

/**
 * Sections rather than cards, and no rules between them: hierarchy is carried by the heading and the
 * spacing, so the only borders left on this surface are the row lists — the boxes the reader is meant
 * to count. The sweep schedule below renders as one more section of the same shape.
 */
export function PracticeReviewSettings({
	workspaceSlug,
	model,
	workspace,
	policy,
	coverage,
}: PracticeReviewSettingsProps) {
	return (
		<div className="space-y-8">
			<ReviewStatusSection workspaceSlug={workspaceSlug} model={model} workspace={workspace} />
			<ReviewTimingSection workspace={workspace} policy={policy} />
			<ReviewedWorkSection policy={policy} coverage={coverage} />
		</div>
	);
}

function ReviewStatusSection({
	workspaceSlug,
	model,
	workspace,
}: Pick<PracticeReviewSettingsProps, "workspaceSlug" | "model" | "workspace">) {
	const modelRunnable = reviewModelRunnable(model);
	// Loading and failed both count as not ready: the switch is closed against them, so the sentence
	// explaining why it is closed has to cover them too.
	const modelNotReady = model.isLoading || model.isError || !modelRunnable;

	return (
		<section className="space-y-4" aria-labelledby="review-status-heading">
			<div className="space-y-1">
				<h2 id="review-status-heading" className="font-semibold text-lg">
					Practice reviews
				</h2>
				{/* Whether reviews are actually running is the page banner's sentence, not a second one
				    here: two prose state machines over the same facts drift apart. */}
				<p className="text-muted-foreground text-sm">
					Whether new practice reviews can start in this workspace.
				</p>
			</div>
			<Field orientation="horizontal">
				<FieldContent>
					<FieldLabel htmlFor="practice-reviews-enabled">Start practice reviews</FieldLabel>
					<FieldDescription>
						{!workspace.enabled && modelNotReady
							? "This can be turned on once a review model is ready to run."
							: "New work is reviewed while this is on. Switching it off stops new reviews; any already running may finish."}
					</FieldDescription>
				</FieldContent>
				<Switch
					id="practice-reviews-enabled"
					checked={workspace.enabled}
					disabled={workspace.isSaving || (!workspace.enabled && modelNotReady)}
					onCheckedChange={(checked) => workspace.onUpdate({ practicesEnabled: checked })}
				/>
			</Field>
			<ModelReadiness workspaceSlug={workspaceSlug} model={model} runnable={modelRunnable} />
		</section>
	);
}

/**
 * Only the states that need an action of their own. Whether the model is ready is already the page
 * banner's headline; repeating it here as a third widget said the same thing three times on one page,
 * so the healthy state is left as the one thing the banner cannot offer — the way to change it.
 */
function ModelReadiness({
	workspaceSlug,
	model,
	runnable,
}: Pick<PracticeReviewSettingsProps, "workspaceSlug" | "model"> & { runnable: boolean }) {
	if (model.isLoading) {
		return null;
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
			<Link
				to="/w/$workspaceSlug/admin/models"
				params={{ workspaceSlug }}
				className={cn(buttonVariants({ variant: "link", size: "sm" }), "h-auto self-start p-0")}
			>
				Change the review model
			</Link>
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

function ReviewTimingSection({
	workspace,
	policy,
}: Pick<PracticeReviewSettingsProps, "workspace" | "policy">) {
	// Reviews on, but no door open. The page banner cannot see this — it knows the switch and the
	// model, not the triggers — so the only place it can be said is beside the switches that cause it.
	const noWayIn =
		workspace.enabled && !workspace.autoTriggerEnabled && !workspace.manualTriggerEnabled;

	return (
		<section className="space-y-4" aria-labelledby="review-timing-heading">
			<div className="space-y-1">
				<h2 id="review-timing-heading" className="font-semibold text-lg">
					How reviews start
				</h2>
				<p className="text-muted-foreground text-sm">
					Two ways in: the work itself reaches a moment a practice watches for, or somebody asks.
				</p>
			</div>
			{noWayIn ? (
				<Alert variant="warning" role="status">
					<AlertCircle />
					<AlertTitle>Nothing can start a review</AlertTitle>
					<AlertDescription>
						Practice reviews are on, but both ways in are switched off.
					</AlertDescription>
				</Alert>
			) : null}
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
						The <strong>Review this now</strong> button, a backfill of past work, a recurring check,
						and <code>/hephaestus review</code> in a GitLab merge request comment. Turning this off
						stops every one of them.
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
		</section>
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

/**
 * Who the work belongs to, where it lives, and whether feedback still lands once it merges — one
 * section, because every one of them answers the same question, and three headings over four fields
 * made the reader look for a difference that was not there.
 *
 * Matches are exact: a wildcard language here would be a promise the gate cannot keep, since it
 * holds the pull request row and not the diff.
 */
function ReviewedWorkSection({
	policy,
	coverage,
}: Pick<PracticeReviewSettingsProps, "policy" | "coverage">) {
	const settings = policy.settings;
	const scope = settings.reviewScope;
	const [pendingScope, setPendingScope] = useState<WorkspaceReviewScope>();
	const repositoryNames = scope.repositories.map((repository) => repository.nameWithOwner);
	const repositoryOptions = [
		...coverage.repositories.options,
		...repositoryNames
			.filter((name) => !coverage.repositories.options.some((option) => option.value === name))
			.map((name) => ({ value: name, label: `${name} (unavailable)` })),
	];
	const personOptions = [
		...coverage.people.options,
		...scope.personUserIds
			.filter((id) => !coverage.people.options.some((option) => option.value === id))
			.map((id) => ({ value: id, label: `Member ${id} (unavailable)` })),
	];
	const requestScope = (next: WorkspaceReviewScope, widens: boolean) => {
		if (widens) {
			setPendingScope(next);
			coverage.preview.onPreview(next);
		} else policy.onUpdate({ reviewScope: next });
	};
	const replaceRepositories = (names: string[]) => {
		const byName = new Map(
			scope.repositories.map((repository) => [repository.nameWithOwner, repository]),
		);
		const next = {
			...scope,
			repositories: names.map(
				(name) => byName.get(name) ?? { nameWithOwner: name, baseBranches: [] },
			),
		};
		requestScope(
			next,
			names.some((name) => !repositoryNames.includes(name)),
		);
	};
	const summary = settings.coverageSummary;
	const preview = coverage.preview.data;

	return (
		<section className="space-y-6" aria-labelledby="reviewed-work-heading">
			<div className="space-y-1">
				<h2 id="reviewed-work-heading" className="font-semibold text-lg">
					What gets reviewed
				</h2>
				<p className="text-muted-foreground text-sm">
					Which work a review may open, and whether feedback still lands once that work has merged.
				</p>
			</div>

			<Alert>
				<AlertTitle>Effective coverage</AlertTitle>
				<AlertDescription>
					Hephaestus currently reviews work in {summary.coveredRepositories} of{" "}
					{summary.monitoredRepositories} monitored repositories, authored by{" "}
					{summary.coveredPeople} of {summary.eligiblePeople} eligible linked members. This
					population produced approximately {summary.recentReviewVolume} reviews in the last{" "}
					{summary.estimateWindowDays} days.
				</AlertDescription>
			</Alert>

			<Field>
				<FieldLabel id="repositories-covered-label">Repositories covered</FieldLabel>
				<FieldDescription>
					Choose all monitored repositories or an exact selected set.
				</FieldDescription>
				<RadioGroup
					aria-labelledby="repositories-covered-label"
					value={scope.repositoryMode}
					disabled={policy.isSaving}
					onValueChange={(value) => {
						const mode = value as WorkspaceReviewScope["repositoryMode"];
						requestScope({ ...scope, repositoryMode: mode }, mode === "ALL_MONITORED");
					}}
				>
					<label className="flex items-center gap-2" htmlFor="repositories-all">
						<RadioGroupItem id="repositories-all" value="ALL_MONITORED" />
						All monitored repositories
					</label>
					<label className="flex items-center gap-2" htmlFor="repositories-selected">
						<RadioGroupItem id="repositories-selected" value="SELECTED" />
						Selected repositories
					</label>
				</RadioGroup>
				{scope.repositoryMode === "SELECTED" ? (
					<div className="space-y-4">
						<FacetMultiSelect
							id="covered-repositories"
							title="Choose repositories"
							variant="field"
							options={repositoryOptions}
							selected={repositoryNames}
							onChange={replaceRepositories}
							disabled={policy.isSaving || coverage.repositories.isLoading}
							emptyLabel={
								coverage.repositories.isError
									? "Repositories unavailable"
									: "No monitored repositories"
							}
						/>
						{scope.repositories.map((repository) => (
							<ScopeList
								key={repository.nameWithOwner}
								id={`branches-${repository.nameWithOwner.replaceAll("/", "-")}`}
								label={`Base branches for ${repository.nameWithOwner}`}
								description="Leave empty for every base branch in this repository. Names are exact."
								placeholder="main"
								values={repository.baseBranches}
								disabled={policy.isSaving}
								onChange={(baseBranches) => {
									const next = {
										...scope,
										repositories: scope.repositories.map((entry) =>
											entry.nameWithOwner === repository.nameWithOwner
												? { ...entry, baseBranches }
												: entry,
										),
									};
									requestScope(next, baseBranches.length < repository.baseBranches.length);
								}}
							/>
						))}
						{scope.repositories.length === 0 ? (
							<p className="text-muted-foreground text-sm">No repositories are covered.</p>
						) : null}
					</div>
				) : null}
			</Field>

			<Field>
				<FieldLabel id="people-covered-label">People covered</FieldLabel>
				<FieldDescription>Only linked human workspace members are eligible.</FieldDescription>
				<RadioGroup
					aria-labelledby="people-covered-label"
					value={scope.personMode}
					disabled={policy.isSaving}
					onValueChange={(value) => {
						const mode = value as WorkspaceReviewScope["personMode"];
						requestScope({ ...scope, personMode: mode }, mode === "ALL_ELIGIBLE");
					}}
				>
					<label className="flex items-center gap-2" htmlFor="people-all">
						<RadioGroupItem id="people-all" value="ALL_ELIGIBLE" />
						All eligible linked members
					</label>
					<label className="flex items-center gap-2" htmlFor="people-selected">
						<RadioGroupItem id="people-selected" value="SELECTED" />
						Selected members
					</label>
				</RadioGroup>
				{scope.personMode === "SELECTED" ? (
					<div className="space-y-2">
						<FacetMultiSelect
							id="covered-people"
							title="Choose members"
							variant="field"
							options={personOptions}
							selected={scope.personUserIds}
							onChange={(personUserIds) =>
								requestScope(
									{ ...scope, personUserIds },
									personUserIds.some((id) => !scope.personUserIds.includes(id)),
								)
							}
							disabled={policy.isSaving || coverage.people.isLoading}
							emptyLabel={coverage.people.isError ? "Members unavailable" : "No linked members"}
						/>
						{scope.personUserIds.length === 0 ? (
							<p className="text-muted-foreground text-sm">No people are covered.</p>
						) : null}
					</div>
				) : null}
			</Field>

			<Field orientation="horizontal">
				<FieldContent>
					<FieldLabel htmlFor="policy-delivery-active">Delivery active</FieldLabel>
					<FieldDescription>
						Pausing keeps review running but terminally withholds new external feedback. Resuming
						never sends older work.
					</FieldDescription>
				</FieldContent>
				<Switch
					id="policy-delivery-active"
					checked={settings.deliveryStatus === "ACTIVE"}
					disabled={policy.isSaving}
					onCheckedChange={(checked) =>
						policy.onUpdate({ deliveryStatus: checked ? "ACTIVE" : "PAUSED" })
					}
				/>
			</Field>

			<Field orientation="horizontal">
				<FieldContent>
					<FieldLabel htmlFor="policy-deliver-merged">Post feedback after merge</FieldLabel>
					<FieldDescription>
						A review that finishes after the work merged still posts its feedback.
					</FieldDescription>
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

			<AlertDialog
				open={pendingScope !== undefined}
				onOpenChange={(open) => !open && setPendingScope(undefined)}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Widen practice-review coverage?</AlertDialogTitle>
						<AlertDialogDescription>
							This may admit more new work. Historical work will not be released.
						</AlertDialogDescription>
					</AlertDialogHeader>
					{coverage.preview.isPending ? (
						<p role="status" className="text-muted-foreground text-sm">
							Calculating the proposed coverage…
						</p>
					) : coverage.preview.isError ? (
						<Alert variant="warning">
							<AlertCircle />
							<AlertTitle>Couldn't preview this change</AlertTitle>
							<AlertDescription>
								<Button
									variant="outline"
									size="sm"
									onClick={() => pendingScope && coverage.preview.onPreview(pendingScope)}
								>
									Retry
								</Button>
							</AlertDescription>
						</Alert>
					) : preview ? (
						<div className="space-y-2 text-sm">
							<p>
								Monitored repositories covered:{" "}
								<strong>{preview.current.coveredRepositories}</strong>
								{" → "}
								<strong>{preview.proposed.coveredRepositories}</strong> of{" "}
								{preview.proposed.monitoredRepositories}
							</p>
							<p>
								Eligible linked members covered: <strong>{preview.current.coveredPeople}</strong>
								{" → "}
								<strong>{preview.proposed.coveredPeople}</strong> of{" "}
								{preview.proposed.eligiblePeople}
							</p>
							<p className="text-muted-foreground text-xs">
								Workspace-wide context: {preview.proposed.recentReviewVolume} reviews ran in the
								last {preview.proposed.estimateWindowDays} days across all review coverage, not just
								this proposed population.
							</p>
						</div>
					) : null}
					<AlertDialogFooter>
						<AlertDialogCancel>Keep current coverage</AlertDialogCancel>
						<AlertDialogAction
							disabled={!preview || coverage.preview.isPending || coverage.preview.isError}
							onClick={() => {
								if (pendingScope) policy.onUpdate({ reviewScope: pendingScope });
								setPendingScope(undefined);
							}}
						>
							Widen coverage
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</section>
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
