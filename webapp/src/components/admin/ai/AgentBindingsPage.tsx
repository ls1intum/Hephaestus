import { ChevronDown } from "lucide-react";
import { type FormEvent, type ReactNode, useId, useState } from "react";
import type {
	AgentBinding,
	AgentBindingRequest,
	AvailableLlmModel,
	WorkspaceLlmUsageReport,
} from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Field,
	FieldDescription,
	FieldError,
	FieldGroup,
	FieldLabel,
	FieldLegend,
	FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { BudgetExhaustedAlert } from "./BudgetExhaustedAlert";
import { ModelPicker, type ModelSelection } from "./ModelPicker";

type Purpose = AgentBinding["purpose"];

interface PurposeMeta {
	purpose: Purpose;
	title: string;
	description: string;
}

const PURPOSES: PurposeMeta[] = [
	{
		purpose: "PRACTICE_DETECTION",
		title: "Practice feedback",
		description: "Reviews pull requests, issues, and conversations.",
	},
	{
		purpose: "MENTOR",
		title: "Mentor",
		description: "Powers the mentor chat.",
	},
];

export const PURPOSE_TITLES: Record<Purpose, string> = Object.fromEntries(
	PURPOSES.map((meta) => [meta.purpose, meta.title]),
) as Record<Purpose, string>;

/** Mirrors `AgentBindingLimits` on the server, which the generated client carries only as prose. */
const MIN_TIMEOUT_SECONDS = 30;
const MAX_TIMEOUT_SECONDS = 3600;
const MIN_CONCURRENT_JOBS = 1;

const TIMEOUT_CEILING = {
	max: MAX_TIMEOUT_SECONDS,
	error: `Runs stop after an hour, so enter ${MAX_TIMEOUT_SECONDS} seconds or less.`,
};

function bindingToSelection(binding?: AgentBinding): ModelSelection | null {
	if (binding?.instanceModelId != null) return { scope: "SHARED", id: binding.instanceModelId };
	if (binding?.workspaceModelId != null)
		return { scope: "WORKSPACE", id: binding.workspaceModelId };
	return null;
}

/**
 * A numeric field held as the string the user typed, so an emptied input stays empty instead of
 * collapsing to `Number("") === 0` and silently saving a zero.
 */
interface ParsedNumber {
	value: number | null;
	error: string | null;
}

function parseWholeNumber(
	raw: string,
	min: number,
	unit: string,
	ceiling?: { max: number; error: string },
): ParsedNumber {
	const trimmed = raw.trim();
	if (trimmed === "") {
		return { value: null, error: `Enter a number of ${unit}.` };
	}
	const parsed = Number(trimmed);
	if (!Number.isInteger(parsed) || parsed < min) {
		return { value: null, error: `Enter a whole number of ${unit}, ${min} or more.` };
	}
	if (ceiling && parsed > ceiling.max) {
		return { value: null, error: ceiling.error };
	}
	return { value: parsed, error: null };
}

export interface AgentBindingsPageProps {
	workspaceSlug: string;
	bindings: AgentBinding[];
	availableModels: AvailableLlmModel[];
	practicesEnabled: boolean;
	mentorEnabled: boolean;
	/** The workspace's own AI providers, mounted by the route because this section fetches its own data. */
	providerPanel?: ReactNode;
	usage?: WorkspaceLlmUsageReport;
	isLoading: boolean;
	isError: boolean;
	loadError: unknown;
	pendingPurposes: ReadonlySet<Purpose>;
	/**
	 * How many writes *this admin* has completed against each purpose. A card reseeds from the server
	 * binding exactly when they save or turn it off, and never on a background refetch — which would
	 * discard the edits they have open.
	 */
	saveRevisions?: Partial<Record<Purpose, number>>;
	onRetry: () => void;
	onSave: (purpose: Purpose, body: AgentBindingRequest) => void;
	onTurnOff: (purpose: Purpose) => void;
}

export function AgentBindingsPage({
	workspaceSlug,
	bindings,
	availableModels,
	practicesEnabled,
	mentorEnabled,
	providerPanel,
	usage,
	isLoading,
	isError,
	loadError,
	pendingPurposes,
	saveRevisions,
	onRetry,
	onSave,
	onTurnOff,
}: AgentBindingsPageProps) {
	const bindingFor = (purpose: Purpose) => bindings.find((b) => b.purpose === purpose);
	const featureEnabled = (purpose: Purpose): boolean =>
		purpose === "MENTOR" ? mentorEnabled : practicesEnabled;

	return (
		<div className="mx-auto w-full max-w-4xl">
			<div className="mb-6">
				<h1 className="text-3xl font-bold tracking-tight">AI models</h1>
			</div>

			{(usage?.ownProviderPaused || usage?.instancePaused) && (
				<div className="mb-6 space-y-3">
					{usage.ownProviderPaused && (
						<BudgetExhaustedAlert
							scope="own"
							verdict={usage.ownProviderBudgetVerdict}
							unpricedEventCount={usage.unpricedEventCount}
							context="models"
							workspaceSlug={workspaceSlug}
						/>
					)}
					{usage.instancePaused && (
						<BudgetExhaustedAlert
							scope="shared"
							verdict={usage.instanceBudgetVerdict}
							unpricedEventCount={usage.unpricedEventCount}
							context="models"
							workspaceSlug={workspaceSlug}
						/>
					)}
				</div>
			)}

			{isError ? (
				<QueryErrorAlert error={loadError} title="Couldn't load AI models" onRetry={onRetry} />
			) : isLoading ? (
				<div className="flex h-40 items-center justify-center">
					<Spinner className="size-6" />
				</div>
			) : (
				<div className="space-y-6">
					<section className="space-y-4">
						<h2 className="text-lg font-semibold">Tasks</h2>
						{PURPOSES.map((meta) => (
							<AgentPurposeCard
								// Keyed on this admin's own writes, not on the bound model id: that would remount
								// over whatever they have typed the moment *another* admin repointed the purpose.
								key={`${meta.purpose}:${saveRevisions?.[meta.purpose] ?? 0}`}
								meta={meta}
								binding={bindingFor(meta.purpose)}
								availableModels={availableModels}
								featureEnabled={featureEnabled(meta.purpose)}
								pending={pendingPurposes.has(meta.purpose)}
								onSave={onSave}
								onTurnOff={onTurnOff}
							/>
						))}
					</section>

					{providerPanel && <section className="space-y-4">{providerPanel}</section>}
				</div>
			)}
		</div>
	);
}

interface AgentPurposeCardProps {
	meta: PurposeMeta;
	binding?: AgentBinding;
	availableModels: AvailableLlmModel[];
	featureEnabled: boolean;
	pending: boolean;
	onSave: (purpose: Purpose, body: AgentBindingRequest) => void;
	onTurnOff: (purpose: Purpose) => void;
}

function AgentPurposeCard({
	meta,
	binding,
	availableModels,
	featureEnabled,
	pending,
	onSave,
	onTurnOff,
}: AgentPurposeCardProps) {
	const cardLabelId = useId();
	const [selection, setSelection] = useState<ModelSelection | null>(bindingToSelection(binding));
	const [enabled, setEnabled] = useState(binding?.enabled ?? true);
	const [timeoutSeconds, setTimeoutSeconds] = useState(String(binding?.timeoutSeconds ?? 600));
	const [maxConcurrentJobs, setMaxConcurrentJobs] = useState(
		String(binding?.maxConcurrentJobs ?? 3),
	);
	const [allowInternet, setAllowInternet] = useState(binding?.allowInternet ?? false);
	const [showAdvanced, setShowAdvanced] = useState(false);
	const [showErrors, setShowErrors] = useState(false);

	const noModels = availableModels.length === 0;

	const timeout = parseWholeNumber(timeoutSeconds, MIN_TIMEOUT_SECONDS, "seconds", TIMEOUT_CEILING);
	const concurrency = parseWholeNumber(maxConcurrentJobs, MIN_CONCURRENT_JOBS, "runs");
	const modelError = showErrors && !selection ? "Choose the model this runs on." : null;
	const timeoutError = showErrors ? timeout.error : null;
	const concurrencyError = showErrors ? concurrency.error : null;

	const modelHintId = `${meta.purpose}-model-hint`;
	const modelErrorId = `${meta.purpose}-model-error`;
	const timeoutErrorId = `${meta.purpose}-timeout-error`;
	const concurrencyErrorId = `${meta.purpose}-concurrency-error`;
	const modelDescribedBy =
		[noModels ? modelHintId : null, modelError ? modelErrorId : null].filter(Boolean).join(" ") ||
		undefined;

	const handleSubmit = (event: FormEvent) => {
		event.preventDefault();
		if (!selection || timeout.value == null || concurrency.value == null) {
			// Save stays enabled precisely so this reveals *why*; the disclosure opens because the
			// offending field may be inside it.
			setShowErrors(true);
			if (timeout.value == null || concurrency.value == null) setShowAdvanced(true);
			return;
		}
		onSave(meta.purpose, {
			instanceModelId: selection.scope === "SHARED" ? selection.id : undefined,
			workspaceModelId: selection.scope === "WORKSPACE" ? selection.id : undefined,
			timeoutSeconds: timeout.value,
			maxConcurrentJobs: concurrency.value,
			allowInternet,
			enabled,
		});
	};

	return (
		<Card role="region" aria-labelledby={cardLabelId}>
			<CardHeader>
				<div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
					<div className="min-w-0 flex-1">
						<CardTitle id={cardLabelId}>{meta.title}</CardTitle>
						<CardDescription>{meta.description}</CardDescription>
					</div>
					{!featureEnabled ? (
						<Badge variant="secondary">Workspace off</Badge>
					) : (
						binding &&
						(binding.ready ? (
							<Badge variant="secondary">Ready</Badge>
						) : (
							<Badge variant="destructive">Not ready</Badge>
						))
					)}
				</div>
			</CardHeader>
			{/* noValidate: left to the browser, `min` blocks submit with a bubble `FieldError` can't explain. */}
			<form onSubmit={handleSubmit} noValidate>
				<CardContent className="space-y-4">
					<FieldGroup>
						<Field data-invalid={Boolean(modelError)}>
							<FieldLabel htmlFor={`${meta.purpose}-model`}>{meta.title} runs on</FieldLabel>
							<ModelPicker
								id={`${meta.purpose}-model`}
								availableModels={availableModels}
								value={selection}
								onChange={(next) => {
									setSelection(next);
									setShowErrors(false);
								}}
								disabled={pending || noModels}
								invalid={Boolean(modelError)}
								aria-describedby={modelDescribedBy}
							/>
							{noModels && (
								<FieldDescription id={modelHintId}>
									No models are available yet. Ask your host to share one, or connect your own AI
									provider below.
								</FieldDescription>
							)}
							{modelError && <FieldError id={modelErrorId}>{modelError}</FieldError>}
						</Field>

						<Field orientation="horizontal">
							<FieldLabel htmlFor={`${meta.purpose}-enabled`}>Active</FieldLabel>
							<Switch
								id={`${meta.purpose}-enabled`}
								checked={enabled}
								onCheckedChange={setEnabled}
								disabled={pending}
							/>
						</Field>
					</FieldGroup>

					<Collapsible open={showAdvanced} onOpenChange={setShowAdvanced}>
						<CollapsibleTrigger
							render={
								<Button type="button" variant="ghost" size="sm" className="-ml-2 group/adv">
									Advanced
									<ChevronDown
										className="transition-transform group-aria-expanded/adv:rotate-180"
										aria-hidden
									/>
								</Button>
							}
						/>
						<CollapsibleContent>
							<FieldSet className="pt-4">
								<FieldLegend variant="label">Run limits</FieldLegend>
								<FieldGroup>
									<Field data-invalid={Boolean(timeoutError)}>
										<FieldLabel htmlFor={`${meta.purpose}-timeout`}>Timeout (seconds)</FieldLabel>
										<Input
											id={`${meta.purpose}-timeout`}
											type="number"
											inputMode="numeric"
											min={MIN_TIMEOUT_SECONDS}
											max={MAX_TIMEOUT_SECONDS}
											value={timeoutSeconds}
											aria-invalid={Boolean(timeoutError)}
											aria-describedby={timeoutError ? timeoutErrorId : undefined}
											onChange={(e) => {
												setTimeoutSeconds(e.target.value);
												setShowErrors(false);
											}}
											disabled={pending}
										/>
										{timeoutError && <FieldError id={timeoutErrorId}>{timeoutError}</FieldError>}
									</Field>
									<Field data-invalid={Boolean(concurrencyError)}>
										<FieldLabel htmlFor={`${meta.purpose}-concurrency`}>
											Max concurrent runs
										</FieldLabel>
										<Input
											id={`${meta.purpose}-concurrency`}
											type="number"
											inputMode="numeric"
											min={MIN_CONCURRENT_JOBS}
											value={maxConcurrentJobs}
											aria-invalid={Boolean(concurrencyError)}
											aria-describedby={concurrencyError ? concurrencyErrorId : undefined}
											onChange={(e) => {
												setMaxConcurrentJobs(e.target.value);
												setShowErrors(false);
											}}
											disabled={pending}
										/>
										{concurrencyError && (
											<FieldError id={concurrencyErrorId}>{concurrencyError}</FieldError>
										)}
									</Field>
									<Field orientation="horizontal">
										<FieldLabel htmlFor={`${meta.purpose}-internet`}>Internet access</FieldLabel>
										<Switch
											id={`${meta.purpose}-internet`}
											checked={allowInternet}
											onCheckedChange={setAllowInternet}
											disabled={pending}
										/>
									</Field>
								</FieldGroup>
							</FieldSet>
						</CollapsibleContent>
					</Collapsible>

					<div className="flex justify-end gap-2">
						{binding && (
							<Button
								type="button"
								variant="outline"
								size="sm"
								onClick={() => onTurnOff(meta.purpose)}
								disabled={pending}
							>
								Turn off
							</Button>
						)}
						<Button type="submit" size="sm" disabled={pending}>
							Save
						</Button>
					</div>
				</CardContent>
			</form>
		</Card>
	);
}
