import { Link } from "@tanstack/react-router";
import { AlertCircle, ChevronDownIcon, FolderGitIcon, UsersIcon, XIcon } from "lucide-react";
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
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { ButtonGroup, ButtonGroupText } from "@/components/ui/button-group";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	InputGroup,
	InputGroupAddon,
	InputGroupButton,
	InputGroupInput,
} from "@/components/ui/input-group";
import {
	Item,
	ItemActions,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemTitle,
} from "@/components/ui/item";
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
 *
 * <p>Four sections for four questions an operator asks separately: may a review start, what starts
 * one, whose work it may open, and whether the result may leave. Coverage and delivery were one
 * section until pausing — the control reached for when something is going wrong — ended up as the
 * second-to-last switch on the page, indistinguishable from a preference.
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
			<FeedbackDeliverySection policy={policy} />
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
 * The two halves of one population: where the work lives and whose it is. Both must admit a piece of
 * work before a review opens, which is the fact the page has to carry — two lists side by side read
 * as "either" to everybody who has not seen the resolver.
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
	const coversNobody =
		(scope.repositoryMode === "SELECTED" && scope.repositories.length === 0) ||
		(scope.personMode === "SELECTED" && scope.personUserIds.length === 0);
	// Counted from the scope on screen rather than read off the saved summary: the summary is a
	// server read that lands after the write, so trusting it puts "3 of 3 covered" above a list
	// holding one repository. Only the totals and the volume come from the server, and neither of
	// those moves when the selection does.
	const coveredRepositories =
		scope.repositoryMode === "ALL_MONITORED"
			? summary.monitoredRepositories
			: scope.repositories.length;
	const coveredPeople =
		scope.personMode === "ALL_ELIGIBLE" ? summary.eligiblePeople : scope.personUserIds.length;
	// A repository can leave the monitored set while it is still named here, and then it quietly
	// covers nothing. Only claimed once the list is actually known: a failed or pending load holds
	// no repositories either, and every row saying "not monitored" over a dropped request is worse
	// than saying nothing.
	const monitoredNow = (nameWithOwner: string) =>
		coverage.repositories.isLoading ||
		coverage.repositories.isError ||
		coverage.repositories.options.some((option) => option.value === nameWithOwner);

	return (
		<section className="space-y-6" aria-labelledby="reviewed-work-heading">
			<div className="space-y-1">
				<h2 id="reviewed-work-heading" className="font-semibold text-lg">
					What gets reviewed
				</h2>
				<p className="text-muted-foreground text-sm">
					A review opens only for work that is in one of these repositories <strong>and</strong>{" "}
					written by one of these people. About {summary.recentReviewVolume} ran in the last{" "}
					{summary.estimateWindowDays} days.
				</p>
			</div>

			{/* One consequence, said once, rather than the same grey sentence under each of the two
			    lists — an empty selection on either axis stops everything, not half of it. */}
			{coversNobody ? (
				<Alert variant="warning" role="status">
					<AlertCircle />
					<AlertTitle>Nothing is being reviewed</AlertTitle>
					<AlertDescription>
						A selected list is empty, and an empty list covers nobody. Pick at least one repository
						and one person, or go back to covering all of them.
					</AlertDescription>
				</Alert>
			) : null}

			<Field>
				<CoverageLabel
					id="repositories-covered-label"
					label="Repositories"
					covered={coveredRepositories}
					total={summary.monitoredRepositories}
					noun="monitored"
				/>
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
					<div className="space-y-3">
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
						{scope.repositories.length === 0 ? (
							<Empty className="border py-6">
								<EmptyHeader>
									<EmptyMedia variant="icon">
										<FolderGitIcon />
									</EmptyMedia>
									<EmptyTitle>No repositories are covered.</EmptyTitle>
									<EmptyDescription>
										Pick the repositories this workspace should review.
									</EmptyDescription>
								</EmptyHeader>
							</Empty>
						) : (
							<ItemGroup>
								{scope.repositories.map((repository) => (
									<RepositoryScopeRow
										key={repository.nameWithOwner}
										nameWithOwner={repository.nameWithOwner}
										baseBranches={repository.baseBranches}
										monitored={monitoredNow(repository.nameWithOwner)}
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
							</ItemGroup>
						)}
					</div>
				) : null}
			</Field>

			<Field>
				<CoverageLabel
					id="people-covered-label"
					label="People"
					covered={coveredPeople}
					total={summary.eligiblePeople}
					noun="linked"
				/>
				<FieldDescription>
					Somebody has to have signed in to Hephaestus at least once before their work can be
					reviewed.
				</FieldDescription>
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
						Everyone who has signed in
					</label>
					<label className="flex items-center gap-2" htmlFor="people-selected">
						<RadioGroupItem id="people-selected" value="SELECTED" />
						Selected people
					</label>
				</RadioGroup>
				{scope.personMode === "SELECTED" ? (
					<div className="space-y-3">
						<FacetMultiSelect
							id="covered-people"
							title="Choose people"
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
							<Empty className="border py-6">
								<EmptyHeader>
									<EmptyMedia variant="icon">
										<UsersIcon />
									</EmptyMedia>
									<EmptyTitle>No people are covered.</EmptyTitle>
									<EmptyDescription>Pick whose work this workspace should review.</EmptyDescription>
								</EmptyHeader>
							</Empty>
						) : (
							// Chips rather than the combobox trigger's "+3": during a pilot the roster is the
							// thing being checked, and a popover you have to open to read is not a roster.
							<ul className="flex flex-wrap gap-2">
								{scope.personUserIds.map((userId) => (
									<li key={userId}>
										<RemovableToken
											label={
												personOptions.find((option) => option.value === userId)?.label ??
												`Member ${userId}`
											}
											removeLabel={`Remove ${
												personOptions.find((option) => option.value === userId)?.label ??
												`member ${userId}`
											} from covered people`}
											disabled={policy.isSaving}
											onRemove={() =>
												requestScope(
													{
														...scope,
														personUserIds: scope.personUserIds.filter((id) => id !== userId),
													},
													false,
												)
											}
										/>
									</li>
								))}
							</ul>
						)}
					</div>
				) : null}
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

/**
 * Whether the already-composed feedback may leave Hephaestus — its own section, because it is the
 * control an operator reaches for when something is going wrong, and it answers a different question
 * from the two lists above it.
 */
function FeedbackDeliverySection({ policy }: Pick<PracticeReviewSettingsProps, "policy">) {
	const settings = policy.settings;
	const paused = settings.deliveryStatus === "PAUSED";

	return (
		<section className="space-y-4" aria-labelledby="feedback-delivery-heading">
			<div className="space-y-1">
				<h2 id="feedback-delivery-heading" className="font-semibold text-lg">
					Sending feedback
				</h2>
				<p className="text-muted-foreground text-sm">
					Whether finished feedback may leave Hephaestus and reach the people it is about.
				</p>
			</div>
			{/* Paused is a state somebody has to be able to notice weeks later, when the question is
			    "why has nobody heard anything" — a switch left of centre does not answer that. */}
			{paused ? (
				<Alert variant="warning" role="status">
					<AlertCircle />
					<AlertTitle>Sending is paused</AlertTitle>
					<AlertDescription>
						Reviews still run and you can still read them here. Nothing prepared while this is
						paused is sent when you resume — only work reviewed afterwards.
					</AlertDescription>
				</Alert>
			) : null}
			<Field orientation="horizontal">
				<FieldContent>
					<FieldLabel htmlFor="policy-delivery-active">
						Send feedback
						<Badge variant={paused ? "warning" : "success"}>{paused ? "Paused" : "Active"}</Badge>
					</FieldLabel>
					<FieldDescription>
						Turning this off stops every message and comment at once. Coverage and practice settings
						are kept.
					</FieldDescription>
				</FieldContent>
				<Switch
					id="policy-delivery-active"
					checked={!paused}
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
		</section>
	);
}

/** The label of a coverage list, carrying how much of the available population it currently admits. */
function CoverageLabel({
	id,
	label,
	covered,
	total,
	noun,
}: {
	id: string;
	label: string;
	covered: number;
	total: number;
	noun: string;
}) {
	return (
		<div className="flex items-baseline justify-between gap-3">
			<FieldLabel id={id}>{label}</FieldLabel>
			<span className="text-muted-foreground text-sm">
				{covered} of {total} {noun}
			</span>
		</div>
	);
}

/**
 * One covered repository. Its branch restriction is the row's own subtitle rather than a field
 * beside it: the restriction belongs to this repository, and a stack of sibling branch editors made
 * a five-repository pilot longer than the rest of the page put together.
 */
function RepositoryScopeRow({
	nameWithOwner,
	baseBranches,
	monitored,
	disabled,
	onChange,
}: {
	nameWithOwner: string;
	baseBranches: string[];
	monitored: boolean;
	disabled: boolean;
	onChange: (next: string[]) => void;
}) {
	const editorId = `branches-${nameWithOwner.replaceAll("/", "-")}`;

	return (
		<Collapsible>
			<Item variant="outline" size="sm" role="listitem" className="flex-wrap">
				<ItemContent>
					<ItemTitle className="min-w-0 gap-2">
						<span className="truncate font-mono" title={nameWithOwner}>
							{nameWithOwner}
						</span>
						{monitored ? null : <Badge variant="warning">Not monitored</Badge>}
					</ItemTitle>
					<ItemDescription>
						{monitored
							? baseBranches.length === 0
								? "Every base branch"
								: `Only ${baseBranches.join(", ")}`
							: "This workspace no longer syncs this repository, so nothing in it is reviewed."}
					</ItemDescription>
				</ItemContent>
				<ItemActions>
					<CollapsibleTrigger
						render={
							<Button variant="outline" size="sm" disabled={disabled} className="group/branches">
								Base branches
								<span className="sr-only"> for {nameWithOwner}</span>
								<ChevronDownIcon
									aria-hidden
									className="transition-transform group-aria-expanded/branches:rotate-180"
								/>
							</Button>
						}
					/>
				</ItemActions>
				<CollapsibleContent className="w-full">
					<BaseBranchEditor
						id={editorId}
						nameWithOwner={nameWithOwner}
						values={baseBranches}
						disabled={disabled}
						onChange={onChange}
					/>
				</CollapsibleContent>
			</Item>
		</Collapsible>
	);
}

function BaseBranchEditor({
	id,
	nameWithOwner,
	values,
	disabled,
	onChange,
}: {
	id: string;
	nameWithOwner: string;
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
		<Field data-invalid={duplicate || undefined} className="border-t pt-3">
			{/* The repository is already the row's title, so the visible label does not repeat it — but a
			    screen reader listing the form's fields sees one of these per covered repository, and
			    "Only these base branches" three times over names nothing. */}
			<FieldLabel htmlFor={id}>
				Only these base branches
				<span className="sr-only"> for {nameWithOwner}</span>
			</FieldLabel>
			<FieldDescription id={descriptionId}>
				Every base branch, unless you name some here. A name has to match the branch exactly.
			</FieldDescription>
			<InputGroup data-invalid={duplicate || undefined}>
				<InputGroupInput
					id={id}
					value={draft}
					placeholder="main"
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
				<InputGroupAddon align="inline-end">
					<InputGroupButton
						variant="outline"
						disabled={disabled || trimmed.length === 0 || duplicate}
						onClick={add}
					>
						{/* One editor per repository, so every Add would otherwise answer to the same name; the
						    visible word still opens the name a voice-control user says (WCAG 2.2 SC 2.5.3). */}
						Add
						<span className="sr-only"> to base branches for {nameWithOwner}</span>
					</InputGroupButton>
				</InputGroupAddon>
			</InputGroup>
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
				<ul className="flex flex-wrap gap-2">
					{values.map((value) => (
						<li key={value}>
							<RemovableToken
								label={value}
								mono
								removeLabel={`Remove ${value} from base branches for ${nameWithOwner}`}
								disabled={disabled}
								onRemove={() => onChange(values.filter((entry) => entry !== value))}
							/>
						</li>
					))}
				</ul>
			) : null}
		</Field>
	);
}

/** A chosen value and the way back out of it, sized to sit in a wrapping row of its siblings. */
function RemovableToken({
	label,
	mono = false,
	removeLabel,
	disabled,
	onRemove,
}: {
	label: string;
	mono?: boolean;
	removeLabel: string;
	disabled: boolean;
	onRemove: () => void;
}) {
	return (
		<ButtonGroup>
			<ButtonGroupText className={cn("h-8 min-w-0 max-w-[60vw] sm:max-w-xs", mono && "font-mono")}>
				<span className="truncate" title={label}>
					{label}
				</span>
			</ButtonGroupText>
			<Button
				variant="outline"
				size="sm"
				className="h-8"
				aria-label={removeLabel}
				disabled={disabled}
				onClick={onRemove}
			>
				<XIcon aria-hidden />
			</Button>
		</ButtonGroup>
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
