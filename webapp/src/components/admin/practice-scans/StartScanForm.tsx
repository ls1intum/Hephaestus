import { Link } from "@tanstack/react-router";
import { AlertCircleIcon, InfoIcon } from "lucide-react";
import { type ReactNode, useId, useState } from "react";

import type { ItemRef } from "@/api/types.gen";
import { EmptyState } from "@/components/common/EmptyState";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { DetailDrawerHeader } from "@/components/layout/detail-drawer/DetailDrawerHeader";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button, buttonVariants } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { DrawerBody, DrawerDescription, DrawerFooter, DrawerTitle } from "@/components/ui/drawer";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldGroup,
	FieldLabel,
	FieldLegend,
	FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { hasText } from "@/lib/text";

import { repositoryName, shortVersion } from "./scan-display";

/** A practice that can be scanned: Hephaestus reviews it, and it is included in scans. */
export interface ScanPracticeOption {
	slug: string;
	name: string;
}

export interface StartScanDraft {
	repositoryId: string;
	practiceSlugs: string[];
	budgetUsd: string;
	reuse: boolean;
}

/**
 * Why the last start did not begin a scan, as the route read the server’s answer. A failure that
 * may not have reached the server is `failed`: starting again repeats the same request, so it cannot
 * start a second scan. Every other kind is a definitive answer.
 */
export type StartScanRefusal =
	| { kind: "active-scan"; scanId: string }
	| { kind: "version-changed"; currentVersion: string }
	| { kind: "refused"; message: string }
	| { kind: "failed"; message: string };

export type StartScanOptions =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "ready"; repositories: readonly ItemRef[]; practices: readonly ScanPracticeOption[] };

export interface StartScanFormProps {
	workspaceSlug: string;
	nested?: boolean;
	options: StartScanOptions;
	pending: boolean;
	refusal?: StartScanRefusal;
	/** The repository version the next start expects, once the server said the head moved. */
	expectedVersion?: string;
	/** What leaving without starting does; the host owns it, because only the host knows where back is. */
	cancel: ReactNode;
	/**
	 * Returns whatever the submit returned: a promise keeps the unsaved-changes guard down until it
	 * settles, so a successful start leaves the form without asking about the draft.
	 */
	onSubmit: (draft: StartScanDraft) => unknown;
}

export const DEFAULT_SCAN_BUDGET_USD = "10";
const BUDGET_PATTERN = /^(?:0|[1-9]\d*)(?:\.\d{1,4})?$/u;

/** The server’s budget rule, said before the request: a decimal from 0.0001 to 100000 USD. */
export function budgetError(value: string): string | undefined {
	const trimmed = value.trim();
	if (!BUDGET_PATTERN.test(trimmed)) {
		return "Enter an amount in US dollars, with at most four decimal places.";
	}
	const amount = Number(trimmed);
	if (amount < 0.0001 || amount > 100_000) {
		return "Enter an amount from 0.0001 to 100000 US dollars.";
	}
	return undefined;
}

function StartScanHeader({ nested }: { nested?: boolean }) {
	return (
		<DetailDrawerHeader nested={nested}>
			<div className="min-w-0 flex-1 space-y-0.5">
				<DrawerTitle>Start scan</DrawerTitle>
				<DrawerDescription>
					Review every file of a repository against the practices you choose. A scan sends no
					feedback to anyone.
				</DrawerDescription>
			</div>
		</DetailDrawerHeader>
	);
}

/**
 * The start form as a guarded drawer level over the scans list. It renders its own loading and error
 * states, because the repositories and practices it offers are read when it opens.
 */
export function StartScanForm(props: StartScanFormProps) {
	const { options, nested, workspaceSlug } = props;
	if (options.status === "loading") {
		return (
			<>
				<StartScanHeader nested={nested} />
				<DrawerBody className="flex flex-col gap-5" aria-busy>
					<Skeleton className="h-8 w-full" />
					<Skeleton className="h-8 w-56" />
					<Skeleton className="h-24 w-full" />
					<Skeleton className="h-8 w-40" />
				</DrawerBody>
			</>
		);
	}
	if (options.status === "error") {
		return (
			<>
				<StartScanHeader nested={nested} />
				<DrawerBody>
					<QueryErrorAlert
						error={options.error}
						title="We could not load what a scan needs"
						onRetry={options.onRetry}
					/>
				</DrawerBody>
			</>
		);
	}
	if (options.practices.length === 0) {
		return (
			<>
				<StartScanHeader nested={nested} />
				<DrawerBody>
					<EmptyState
						icon={<InfoIcon />}
						headingLevel={3}
						title="No practice is included in scans"
						description="In Practice setup, open a practice that Hephaestus reviews and turn on scans for it. Then start a scan here."
						action={
							<Link
								to="/w/$workspaceSlug/admin/practices"
								params={{ workspaceSlug }}
								className={buttonVariants({ variant: "outline" })}
							>
								Open Practice setup
							</Link>
						}
					/>
				</DrawerBody>
			</>
		);
	}
	if (options.repositories.length === 0) {
		return (
			<>
				<StartScanHeader nested={nested} />
				<DrawerBody>
					<EmptyState
						icon={<InfoIcon />}
						headingLevel={3}
						title="No repository can be scanned"
						description="A scan reviews a repository in this workspace’s review scope. Add one to the review scope first."
						action={
							<Link
								to="/w/$workspaceSlug/admin/practices/review"
								params={{ workspaceSlug }}
								search={{}}
								className={buttonVariants({ variant: "outline" })}
							>
								Open Review settings
							</Link>
						}
					/>
				</DrawerBody>
			</>
		);
	}
	return (
		<StartScanDraftForm
			{...props}
			repositories={options.repositories}
			practices={options.practices}
		/>
	);
}

interface StartScanDraftFormProps extends StartScanFormProps {
	repositories: readonly ItemRef[];
	practices: readonly ScanPracticeOption[];
}

function sameDraft(a: StartScanDraft, b: StartScanDraft): boolean {
	return (
		a.repositoryId === b.repositoryId &&
		a.budgetUsd === b.budgetUsd &&
		a.reuse === b.reuse &&
		a.practiceSlugs.length === b.practiceSlugs.length &&
		a.practiceSlugs.every((slug) => b.practiceSlugs.includes(slug))
	);
}

function StartScanDraftForm({
	workspaceSlug,
	nested,
	repositories,
	practices,
	pending,
	refusal,
	expectedVersion,
	cancel,
	onSubmit,
}: StartScanDraftFormProps) {
	const id = useId();
	const [initial] = useState<StartScanDraft>(() => ({
		repositoryId: repositories[0]?.id ?? "",
		practiceSlugs: practices.map((practice) => practice.slug),
		budgetUsd: DEFAULT_SCAN_BUDGET_USD,
		reuse: true,
	}));
	const [draft, setDraft] = useState(initial);
	const [checked, setChecked] = useState(false);
	const unsavedChanges = useUnsavedChanges({
		isDirty: !sameDraft(draft, initial),
		disabled: pending,
		description: "The scan has not started. Your choices will be lost if you leave.",
	});
	const patch = (change: Partial<StartScanDraft>) =>
		setDraft((previous) => ({ ...previous, ...change }));

	const practiceError =
		checked && draft.practiceSlugs.length === 0 ? "Select at least one practice." : undefined;
	const budgetProblem = checked ? budgetError(draft.budgetUsd) : undefined;
	const repositoryError =
		checked && !repositories.some((repository) => repository.id === draft.repositoryId)
			? "Select a repository."
			: undefined;
	const repositoryItems = repositories.map((repository) => ({
		value: repository.id,
		label: repositoryName(repository),
	}));
	const fieldId = (name: string) => `${id}-${name}`;

	const submit = (event: React.SubmitEvent<HTMLFormElement>) => {
		event.preventDefault();
		if (pending) {
			return;
		}
		setChecked(true);
		if (
			draft.practiceSlugs.length === 0 ||
			budgetError(draft.budgetUsd) !== undefined ||
			!repositories.some((repository) => repository.id === draft.repositoryId)
		) {
			return;
		}
		unsavedChanges.track(onSubmit({ ...draft, budgetUsd: draft.budgetUsd.trim() }));
	};

	return (
		<>
			{unsavedChanges.dialog}
			<StartScanHeader nested={nested} />
			<form onSubmit={submit} className="flex min-h-0 flex-1 flex-col" noValidate>
				<DrawerBody className="flex flex-col gap-6">
					{refusal && <RefusalAlert refusal={refusal} workspaceSlug={workspaceSlug} />}
					<fieldset disabled={pending} className="contents">
						<FieldGroup className="gap-5">
							<Field data-invalid={repositoryError === undefined ? undefined : "true"}>
								<FieldLabel htmlFor={fieldId("repository")}>Repository</FieldLabel>
								<Select
									items={repositoryItems}
									value={draft.repositoryId === "" ? null : draft.repositoryId}
									disabled={pending}
									onValueChange={(value) => patch({ repositoryId: value ?? "" })}
								>
									<SelectTrigger
										id={fieldId("repository")}
										className="w-full"
										aria-describedby={fieldId("repository-help")}
									>
										<SelectValue placeholder="Select a repository" />
									</SelectTrigger>
									<SelectContent aria-label="Repository">
										{repositoryItems.map((item) => (
											<SelectItem key={item.value} value={item.value}>
												{item.label}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
								<FieldDescription id={fieldId("repository-help")}>
									Only repositories in this workspace’s review scope can be scanned.
								</FieldDescription>
								{hasText(repositoryError) && <FieldError>{repositoryError}</FieldError>}
							</Field>

							<Field>
								<FieldLabel htmlFor={fieldId("branch")}>Branch</FieldLabel>
								<Input
									id={fieldId("branch")}
									value="Default branch"
									readOnly
									aria-describedby={fieldId("branch-help")}
								/>
								<FieldDescription id={fieldId("branch-help")}>
									{expectedVersion === undefined
										? "A scan reviews the default branch at its latest version, fixed when the scan starts."
										: `The next start expects version ${shortVersion(expectedVersion)}.`}
								</FieldDescription>
							</Field>

							<FieldSet data-invalid={practiceError === undefined ? undefined : "true"}>
								<FieldLegend variant="label">Practices</FieldLegend>
								<FieldDescription id={fieldId("practices-help")}>
									{draft.practiceSlugs.length} of {practices.length} selected. Only practices
									included in scans that Hephaestus reviews are listed.
								</FieldDescription>
								<div className="flex flex-col gap-2">
									{practices.map((practice) => {
										const checkboxId = fieldId(`practice-${practice.slug}`);
										return (
											<Field key={practice.slug} orientation="horizontal">
												<Checkbox
													id={checkboxId}
													checked={draft.practiceSlugs.includes(practice.slug)}
													aria-invalid={practiceError !== undefined}
													aria-describedby={
														practiceError === undefined ? undefined : fieldId("practices-error")
													}
													onCheckedChange={(next) =>
														patch({
															practiceSlugs: next
																? [...draft.practiceSlugs, practice.slug]
																: draft.practiceSlugs.filter((slug) => slug !== practice.slug),
														})
													}
												/>
												<FieldLabel htmlFor={checkboxId} className="font-normal">
													{practice.name}
												</FieldLabel>
											</Field>
										);
									})}
								</div>
								{hasText(practiceError) && (
									<FieldError id={fieldId("practices-error")}>{practiceError}</FieldError>
								)}
							</FieldSet>

							<Field data-invalid={budgetProblem === undefined ? undefined : "true"}>
								<FieldLabel htmlFor={fieldId("budget")}>Budget (US dollars)</FieldLabel>
								<Input
									id={fieldId("budget")}
									value={draft.budgetUsd}
									inputMode="decimal"
									autoComplete="off"
									className="w-full sm:w-40"
									aria-invalid={budgetProblem !== undefined}
									aria-describedby={
										budgetProblem === undefined
											? fieldId("budget-help")
											: `${fieldId("budget-help")} ${fieldId("budget-error")}`
									}
									onChange={(event) => patch({ budgetUsd: event.target.value })}
								/>
								<FieldDescription id={fieldId("budget-help")}>
									The scan stops when the AI model use reaches this amount. Files it did not reach
									are marked not reviewed. The workspace’s AI model must be ready, or the scan does
									not start.
								</FieldDescription>
								{hasText(budgetProblem) && (
									<FieldError id={fieldId("budget-error")}>{budgetProblem}</FieldError>
								)}
							</Field>

							<Field orientation="horizontal">
								<Checkbox
									id={fieldId("reuse")}
									checked={draft.reuse}
									onCheckedChange={(next) => patch({ reuse: next })}
								/>
								<FieldContent>
									<FieldLabel htmlFor={fieldId("reuse")}>Reuse unchanged results</FieldLabel>
									<FieldDescription>
										A file and practice unchanged since an earlier review keep that result instead
										of being reviewed again.
									</FieldDescription>
								</FieldContent>
							</Field>
						</FieldGroup>
					</fieldset>
				</DrawerBody>
				<DrawerFooter>
					{cancel}
					<Button type="submit" disabled={pending}>
						{pending && <Spinner />}
						{pending ? "Starting scan…" : "Start scan"}
					</Button>
				</DrawerFooter>
			</form>
		</>
	);
}

function RefusalAlert({
	refusal,
	workspaceSlug,
}: {
	refusal: StartScanRefusal;
	workspaceSlug: string;
}) {
	switch (refusal.kind) {
		case "active-scan": {
			return (
				<Alert variant="warning">
					<InfoIcon />
					<AlertTitle>Another scan is running</AlertTitle>
					<AlertDescription>
						This workspace runs one scan at a time. Wait for it to finish, or cancel it, then start
						this one.{" "}
						<Link
							to="/w/$workspaceSlug/admin/practices/scans/$scanId"
							params={{ workspaceSlug, scanId: refusal.scanId }}
							className="font-medium underline underline-offset-4"
						>
							Open the running scan
						</Link>
					</AlertDescription>
				</Alert>
			);
		}
		case "version-changed": {
			return (
				<Alert variant="warning">
					<InfoIcon />
					<AlertTitle>The repository changed</AlertTitle>
					<AlertDescription>
						The default branch moved to version {shortVersion(refusal.currentVersion)} while you
						chose. Nothing was started or charged. Start the scan again to review that version.
					</AlertDescription>
				</Alert>
			);
		}
		case "refused": {
			return (
				<Alert variant="destructive">
					<AlertCircleIcon />
					<AlertTitle>The scan did not start</AlertTitle>
					<AlertDescription>{refusal.message}</AlertDescription>
				</Alert>
			);
		}
		case "failed": {
			return (
				<Alert variant="destructive">
					<AlertCircleIcon />
					<AlertTitle>We could not start the scan</AlertTitle>
					<AlertDescription>
						{refusal.message} Your choices are still here. Starting again cannot start a second
						scan.
					</AlertDescription>
				</Alert>
			);
		}
	}
}
