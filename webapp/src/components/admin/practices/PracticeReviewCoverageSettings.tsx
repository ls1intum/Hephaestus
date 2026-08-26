import deepEqual from "fast-deep-equal";
import { AlertCircle, ChevronDownIcon, Loader2Icon } from "lucide-react";
import { useId, useState } from "react";
import type {
	PracticeReviewCoveragePreview,
	PracticeReviewSettings,
	WorkspaceReviewScope,
} from "@/api/types.gen";
import { FacetMultiSelect, type FacetOption } from "@/components/common/FacetMultiSelect";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RemovableToken } from "@/components/common/RemovableToken";
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
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Field, FieldDescription, FieldError, FieldLabel, FieldTitle } from "@/components/ui/field";
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
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";

type CoverageOptionState<TOption> =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "ready"; options: TOption[] };

export type RepositoryCoverageOptions = CoverageOptionState<FacetOption>;
export type PeopleCoverageOptions = CoverageOptionState<FacetOption<number>>;

export interface PracticeReviewCoverageSettingsProps {
	settings: PracticeReviewSettings;
	preview: (scope: WorkspaceReviewScope) => Promise<PracticeReviewCoveragePreview>;
	onSave: (scope: WorkspaceReviewScope, sourceEtag: string) => Promise<void>;
	repositories: RepositoryCoverageOptions;
	people: PeopleCoverageOptions;
}

type Workflow =
	| { status: "editing" }
	| { status: "checking" }
	| { status: "saving" }
	| { status: "error"; action: "preview" | "save" }
	| {
			status: "confirm";
			preview: PracticeReviewCoveragePreview;
			scope: WorkspaceReviewScope;
			sourceEtag: string;
	  };

export function PracticeReviewCoverageSettings({
	settings,
	preview,
	onSave,
	repositories,
	people,
}: PracticeReviewCoverageSettingsProps) {
	const repositoryScopeId = useId();
	const personScopeId = useId();
	const [storedDraft, setStoredDraft] = useState({
		baseScope: settings.reviewScope,
		baseEtag: settings.etag,
		scope: settings.reviewScope,
	});
	const storedDraftChanged = !deepEqual(storedDraft.scope, storedDraft.baseScope);
	const persistedScopeChanged = !deepEqual(storedDraft.baseScope, settings.reviewScope);
	const draft =
		persistedScopeChanged && !storedDraftChanged ? settings.reviewScope : storedDraft.scope;
	const setDraft = (scope: WorkspaceReviewScope) =>
		setStoredDraft((current) => {
			const hasUnmergedChanges =
				!deepEqual(current.scope, current.baseScope) &&
				!deepEqual(current.scope, settings.reviewScope);
			return {
				baseScope: hasUnmergedChanges ? current.baseScope : settings.reviewScope,
				baseEtag: hasUnmergedChanges ? current.baseEtag : settings.etag,
				scope,
			};
		});
	const [workflow, setWorkflow] = useState<Workflow>({ status: "editing" });
	const persisted = settings.reviewScope;
	const dirty = !deepEqual(draft, persisted);
	const conflicted = storedDraftChanged && persistedScopeChanged;
	const sourceEtag = persistedScopeChanged ? storedDraft.baseEtag : settings.etag;
	const busy = workflow.status === "checking" || workflow.status === "saving";
	const unsavedChanges = useUnsavedChanges({
		isDirty: dirty,
		disabled: busy,
		description: "Your review coverage draft will be lost if you leave.",
	});

	const repositoryNames = draft.repositories.map((repository) => repository.nameWithOwner);
	const repositoryOptions = withPersistedOptions(
		repositories,
		repositoryNames,
		(name) => ({ value: name, label: name }),
		(name) => ({ value: name, label: `${name} (unavailable)` }),
	);
	const personOptions = withPersistedOptions(
		people,
		draft.personUserIds,
		(id) => ({ value: id, label: `Member ${id}` }),
		(id) => ({ value: id, label: `Member ${id} (unavailable)` }),
	);

	const monitored = (name: string) =>
		repositories.status !== "ready" || repositories.options.some((option) => option.value === name);
	const coveredRepositories =
		draft.repositoryMode === "ALL_MONITORED"
			? settings.coverageSummary.monitoredRepositories
			: draft.repositories.filter((repository) => monitored(repository.nameWithOwner)).length;
	const eligible = (id: number) =>
		people.status !== "ready" || people.options.some((option) => option.value === id);
	const coveredPeople =
		draft.personMode === "ALL_ELIGIBLE"
			? settings.coverageSummary.eligiblePeople
			: draft.personUserIds.filter(eligible).length;

	const save = async (scope: WorkspaceReviewScope, etag: string) => {
		setWorkflow({ status: "saving" });
		try {
			await onSave(scope, etag);
			setStoredDraft({ baseScope: scope, baseEtag: etag, scope });
			setWorkflow({ status: "editing" });
		} catch {
			setWorkflow({ status: "error", action: "save" });
		}
	};
	const review = async () => {
		if (conflicted) return;
		const scope = draft;
		const etag = sourceEtag;
		setWorkflow({ status: "checking" });
		try {
			const result = await preview(scope);
			if (result.widens)
				setWorkflow({ status: "confirm", preview: result, scope, sourceEtag: etag });
			else await save(scope, etag);
		} catch {
			setWorkflow({ status: "error", action: "preview" });
		}
	};

	return (
		<section className="space-y-6" aria-labelledby="reviewed-work-heading">
			{unsavedChanges.dialog}
			<div className="space-y-1">
				<h2 id="reviewed-work-heading" className="font-semibold text-lg">
					What gets reviewed
				</h2>
				<p className="text-muted-foreground text-sm">
					A review opens only for work that is in one of these repositories <strong>and</strong>{" "}
					written by one of these linked people. Across this workspace, about{" "}
					{settings.coverageSummary.recentReviewVolume} ran in the last{" "}
					{settings.coverageSummary.estimateWindowDays} days.
				</p>
			</div>
			{conflicted ? (
				<Alert variant="warning" role="alert">
					<AlertCircle />
					<AlertTitle>Coverage changed elsewhere</AlertTitle>
					<AlertDescription>
						Your draft is based on older coverage. Load the latest coverage before making this
						change again.
						<Button
							variant="outline"
							size="sm"
							className="mt-2"
							onClick={() => {
								setStoredDraft({
									baseScope: settings.reviewScope,
									baseEtag: settings.etag,
									scope: settings.reviewScope,
								});
								setWorkflow({ status: "editing" });
							}}
						>
							Load latest coverage
						</Button>
					</AlertDescription>
				</Alert>
			) : null}

			<Field>
				<CoverageLabel
					id={`${repositoryScopeId}-label`}
					label="Repositories"
					covered={coveredRepositories}
					total={settings.coverageSummary.monitoredRepositories}
					noun="monitored"
				/>
				<RadioGroup
					aria-labelledby={`${repositoryScopeId}-label`}
					value={draft.repositoryMode}
					disabled={busy}
					onValueChange={(repositoryMode) => setDraft({ ...draft, repositoryMode })}
				>
					<label className="flex items-center gap-2" htmlFor={`${repositoryScopeId}-all`}>
						<RadioGroupItem id={`${repositoryScopeId}-all`} value="ALL_MONITORED" />
						All monitored repositories
					</label>
					<label className="flex items-center gap-2" htmlFor={`${repositoryScopeId}-selected`}>
						<RadioGroupItem id={`${repositoryScopeId}-selected`} value="SELECTED" />
						Selected repositories
					</label>
				</RadioGroup>
				{draft.repositoryMode === "SELECTED" ? (
					<div className="space-y-3">
						{repositories.status === "error" ? (
							<QueryErrorAlert
								error={repositories.error}
								title="Couldn't load repositories"
								onRetry={repositories.onRetry}
							/>
						) : null}
						<FacetMultiSelect
							id="covered-repositories"
							title="Choose repositories"
							variant="field"
							options={repositoryOptions}
							selected={repositoryNames}
							onChange={(names) => {
								const byName = new Map(
									draft.repositories.map((repository) => [repository.nameWithOwner, repository]),
								);
								setDraft({
									...draft,
									repositories: names.map(
										(name) => byName.get(name) ?? { nameWithOwner: name, baseBranches: [] },
									),
								});
							}}
							disabled={busy || repositories.status !== "ready"}
							emptyLabel="No monitored repositories"
						/>
						{draft.repositories.length === 0 ? (
							<EmptySelection noun="repositories" allLabel="all monitored repositories" />
						) : (
							<ItemGroup>
								{draft.repositories.map((repository) => (
									<RepositoryScopeRow
										key={repository.nameWithOwner}
										nameWithOwner={repository.nameWithOwner}
										baseBranches={repository.baseBranches}
										monitored={monitored(repository.nameWithOwner)}
										disabled={busy}
										onChange={(baseBranches) =>
											setDraft({
												...draft,
												repositories: draft.repositories.map((entry) =>
													entry.nameWithOwner === repository.nameWithOwner
														? { ...entry, baseBranches }
														: entry,
												),
											})
										}
									/>
								))}
							</ItemGroup>
						)}
					</div>
				) : null}
			</Field>

			<Field>
				<CoverageLabel
					id={`${personScopeId}-label`}
					label="People"
					covered={coveredPeople}
					total={settings.coverageSummary.eligiblePeople}
					noun="eligible"
				/>
				<FieldDescription>
					Only linked human members who are eligible for practice reviews appear here. Missing or
					unlinked authors are never included automatically.
				</FieldDescription>
				<RadioGroup
					aria-labelledby={`${personScopeId}-label`}
					value={draft.personMode}
					disabled={busy}
					onValueChange={(personMode) => setDraft({ ...draft, personMode })}
				>
					<label className="flex items-center gap-2" htmlFor={`${personScopeId}-all`}>
						<RadioGroupItem id={`${personScopeId}-all`} value="ALL_ELIGIBLE" />
						All eligible linked members
					</label>
					<label className="flex items-center gap-2" htmlFor={`${personScopeId}-selected`}>
						<RadioGroupItem id={`${personScopeId}-selected`} value="SELECTED" />
						Selected people
					</label>
				</RadioGroup>
				{draft.personMode === "SELECTED" ? (
					<div className="space-y-3">
						{people.status === "error" ? (
							<QueryErrorAlert
								error={people.error}
								title="Couldn't load eligible members"
								onRetry={people.onRetry}
							/>
						) : null}
						<FacetMultiSelect
							id="covered-people"
							title="Choose people"
							variant="field"
							options={personOptions}
							selected={draft.personUserIds}
							onChange={(personUserIds) => setDraft({ ...draft, personUserIds })}
							disabled={busy || people.status !== "ready"}
							emptyLabel="No eligible linked members"
						/>
						{draft.personUserIds.length === 0 ? (
							<EmptySelection noun="people" allLabel="all eligible linked members" />
						) : (
							<ul className="flex flex-wrap gap-2">
								{draft.personUserIds.map((userId) => {
									const label =
										personOptions.find((option) => option.value === userId)?.label ??
										`Member ${userId}`;
									return (
										<li key={userId}>
											<RemovableToken
												label={label}
												removeLabel={`Remove ${label} from covered people`}
												disabled={busy}
												onRemove={() =>
													setDraft({
														...draft,
														personUserIds: draft.personUserIds.filter((id) => id !== userId),
													})
												}
											/>
										</li>
									);
								})}
							</ul>
						)}
					</div>
				) : null}
			</Field>

			<div className="flex min-h-12 flex-wrap items-center justify-between gap-3 border-t pt-4">
				<CoverageWorkflowStatus workflow={workflow} dirty={dirty} />
				<div className="flex flex-wrap gap-2">
					<Button
						variant="outline"
						disabled={!dirty || busy}
						onClick={() => {
							setDraft(persisted);
							setWorkflow({ status: "editing" });
						}}
					>
						Discard changes
					</Button>
					<Button
						className="min-w-36"
						disabled={!dirty || busy || conflicted}
						onClick={() => void review()}
					>
						{workflow.status === "checking" ? (
							<>
								<Loader2Icon className="animate-spin" aria-hidden />
								Checking impact…
							</>
						) : workflow.status === "saving" ? (
							<>
								<Loader2Icon className="animate-spin" aria-hidden />
								Saving…
							</>
						) : (
							"Review changes"
						)}
					</Button>
				</div>
			</div>

			<AlertDialog
				open={workflow.status === "confirm"}
				onOpenChange={(open) => !open && setWorkflow({ status: "editing" })}
			>
				<AlertDialogContent className="min-w-0 max-w-[calc(100vw-2rem)] overflow-hidden sm:max-w-lg">
					<AlertDialogHeader>
						<AlertDialogTitle>Widen review coverage?</AlertDialogTitle>
						<AlertDialogDescription>
							More work becomes eligible for review. Feedback still follows each practice's
							authority, the recipient's preference, and workspace delivery status. Earlier work is
							not released or run again.
						</AlertDialogDescription>
					</AlertDialogHeader>
					{workflow.status === "confirm" ? <CoverageImpact preview={workflow.preview} /> : null}
					<AlertDialogFooter>
						<AlertDialogCancel>Keep editing</AlertDialogCancel>
						<AlertDialogAction
							onClick={() => {
								if (workflow.status === "confirm") {
									void save(workflow.scope, workflow.sourceEtag);
								}
							}}
						>
							Apply wider coverage
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</section>
	);
}

function withPersistedOptions<TValue extends string | number>(
	state: CoverageOptionState<FacetOption<TValue>>,
	selected: readonly TValue[],
	unknown: (value: TValue) => FacetOption<TValue>,
	unavailable: (value: TValue) => FacetOption<TValue>,
): FacetOption<TValue>[] {
	if (state.status !== "ready") return selected.map(unknown);
	return [
		...state.options,
		...selected
			.filter((value) => !state.options.some((option) => option.value === value))
			.map(unavailable),
	];
}

function CoverageWorkflowStatus({ workflow, dirty }: { workflow: Workflow; dirty: boolean }) {
	if (workflow.status === "error") {
		return (
			<p role="alert" className="max-w-md text-destructive text-sm">
				{workflow.action === "preview"
					? "Couldn't estimate the impact. Your draft is unchanged; try again."
					: "Couldn't save the coverage. Your draft is unchanged; try again."}
			</p>
		);
	}
	return (
		<p role="status" className="max-w-md text-muted-foreground text-sm">
			{workflow.status === "checking"
				? "Checking the impact of the complete draft…"
				: workflow.status === "saving"
					? "Saving the complete coverage…"
					: dirty
						? "Changes are only a draft until you review them."
						: "Coverage is up to date."}
		</p>
	);
}

function CoverageImpact({ preview }: { preview: PracticeReviewCoveragePreview }) {
	return (
		<div className="min-w-0 space-y-2 break-words text-sm">
			<p>
				Monitored repositories covered: <strong>{preview.current.coveredRepositories}</strong>
				{" → "}
				<strong>{preview.proposed.coveredRepositories}</strong> of{" "}
				{preview.proposed.monitoredRepositories}
			</p>
			<p>
				Eligible linked members covered: <strong>{preview.current.coveredPeople}</strong>
				{" → "}
				<strong>{preview.proposed.coveredPeople}</strong> of {preview.proposed.eligiblePeople}
			</p>
			<p className="text-muted-foreground text-xs">
				Workspace-wide context: {preview.proposed.recentReviewVolume} reviews ran in the last{" "}
				{preview.proposed.estimateWindowDays} days across all review coverage, not just this
				proposed population.
			</p>
		</div>
	);
}

function EmptySelection({ noun, allLabel }: { noun: string; allLabel: string }) {
	return (
		<Alert variant="warning" role="status">
			<AlertCircle />
			<AlertTitle>No {noun} are selected</AlertTitle>
			<AlertDescription>
				An empty selected list covers nobody. Choose{" "}
				{noun === "people" ? "a person" : "a repository"}
				or cover {allLabel}.
			</AlertDescription>
		</Alert>
	);
}

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
			<FieldTitle id={id}>{label}</FieldTitle>
			<span className="text-muted-foreground text-sm">
				{covered} of {total} {noun}
			</span>
		</div>
	);
}

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
	const editorId = useId();
	return (
		<Collapsible>
			<Item variant="outline" size="sm" role="listitem" className="flex-wrap">
				<ItemContent className="min-w-0">
					<ItemTitle className="w-full min-w-0 gap-2">
						<span className="truncate font-mono" title={nameWithOwner}>
							{nameWithOwner}
						</span>
						{monitored ? null : <Badge variant="warning">Not monitored</Badge>}
					</ItemTitle>
					<ItemDescription className="break-all">
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
								Base branches<span className="sr-only"> for {nameWithOwner}</span>
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
			<FieldLabel htmlFor={id}>
				Only these base branches<span className="sr-only"> for {nameWithOwner}</span>
			</FieldLabel>
			<FieldDescription id={descriptionId}>
				Every base branch, unless you name some here. A name has to match the branch exactly.
			</FieldDescription>
			<InputGroup data-invalid={duplicate || undefined}>
				<InputGroupInput
					id={id}
					name={`base-branch-${nameWithOwner}`}
					value={draft}
					autoComplete="off"
					spellCheck={false}
					placeholder="main…"
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
						Add<span className="sr-only"> to base branches for {nameWithOwner}</span>
					</InputGroupButton>
				</InputGroupAddon>
			</InputGroup>
			{duplicate ? <FieldError id={errorId}>{trimmed} is already listed.</FieldError> : null}
			{values.length > 0 ? (
				<ul className="flex flex-wrap gap-2">
					{values.map((value) => (
						<li key={value}>
							<RemovableToken
								label={value}
								className="font-mono"
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
