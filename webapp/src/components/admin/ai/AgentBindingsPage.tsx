import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDown } from "lucide-react";
import { type FormEvent, useState } from "react";
import { toast } from "sonner";
import {
	deleteBindingMutation,
	getAiSettingsOptions,
	getBindingsOptions,
	getBindingsQueryKey,
	getLlmUsageReportOptions,
	upsertBindingMutation,
	workspaceListAvailableLlmModelsOptions,
} from "@/api/@tanstack/react-query.gen";
import type { AgentBinding, AvailableLlmModel } from "@/api/types.gen";
import { currentMonthUtc } from "@/components/admin/usage/usageUtils";
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
import { problemDetailOf } from "@/lib/problem-detail";
import { BudgetExhaustedAlert } from "./BudgetExhaustedAlert";
import { ModelPicker, type ModelSelection } from "./ModelPicker";
import { WorkspaceLlmProviderPanel } from "./WorkspaceLlmProviderPanel";

type Purpose = AgentBinding["purpose"];

interface PurposeMeta {
	purpose: Purpose;
	title: string;
	description: string;
}

const PURPOSES: PurposeMeta[] = [
	{
		purpose: "PRACTICE_DETECTION",
		title: "Practice detection",
		description:
			"The model that reviews pull requests, issues, and conversations for this workspace.",
	},
	{
		purpose: "MENTOR",
		title: "Mentor",
		description: "The model that powers the mentor chat for this workspace.",
	},
];

const MIN_TIMEOUT_SECONDS = 30;
const MIN_CONCURRENT_JOBS = 1;

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
	/** The value to submit, or `null` while the text isn't a usable number. */
	value: number | null;
	/** Why the text isn't usable — rendered as this field's `FieldError`, never as a toast. */
	error: string | null;
}

function parseWholeNumber(raw: string, min: number, unit: string): ParsedNumber {
	const trimmed = raw.trim();
	if (trimmed === "") {
		return { value: null, error: `Enter a number of ${unit}.` };
	}
	const parsed = Number(trimmed);
	if (!Number.isInteger(parsed) || parsed < min) {
		return { value: null, error: `Enter a whole number of ${unit}, ${min} or more.` };
	}
	return { value: parsed, error: null };
}

interface AgentBindingsPageProps {
	workspaceSlug: string;
}

export function AgentBindingsPage({ workspaceSlug }: AgentBindingsPageProps) {
	const bindingsQuery = useQuery({
		...getBindingsOptions({ path: { workspaceSlug } }),
		enabled: Boolean(workspaceSlug),
	});
	const aiSettingsQuery = useQuery({
		...getAiSettingsOptions({ path: { workspaceSlug } }),
		enabled: Boolean(workspaceSlug),
	});
	const availableModelsQuery = useQuery({
		...workspaceListAvailableLlmModelsOptions({ path: { workspaceSlug } }),
		enabled: Boolean(workspaceSlug),
	});
	const usageQuery = useQuery({
		...getLlmUsageReportOptions({ path: { workspaceSlug }, query: { month: currentMonthUtc() } }),
		enabled: Boolean(workspaceSlug),
		staleTime: 60_000,
	});

	const bindings = bindingsQuery.data ?? [];
	const availableModels: AvailableLlmModel[] = availableModelsQuery.data ?? [];
	const bindingFor = (purpose: Purpose) => bindings.find((b) => b.purpose === purpose);

	const featureEnabled = (purpose: Purpose): boolean =>
		purpose === "MENTOR"
			? (aiSettingsQuery.data?.mentorEnabled ?? false)
			: (aiSettingsQuery.data?.practicesEnabled ?? false);

	const isLoading =
		bindingsQuery.isLoading || aiSettingsQuery.isLoading || availableModelsQuery.isLoading;
	const isError = bindingsQuery.isError || aiSettingsQuery.isError || availableModelsQuery.isError;
	// Whichever of the three failed first supplies the ProblemDetail and the status the alert
	// classifies on — a 403 must not offer a Retry that would be refused identically.
	const loadError = bindingsQuery.error ?? aiSettingsQuery.error ?? availableModelsQuery.error;

	const handleRetry = () => {
		bindingsQuery.refetch();
		aiSettingsQuery.refetch();
		availableModelsQuery.refetch();
	};

	return (
		<div className="container mx-auto max-w-4xl py-6">
			<div className="mb-6">
				<h1 className="text-3xl font-bold tracking-tight">AI setup</h1>
				<p className="text-muted-foreground">
					Choose which model runs practice detection and the mentor, or connect a workspace-funded
					provider.
				</p>
			</div>

			{/* The two caps pause independently, so each paused side gets its own banner — the one the
			    workspace can act on first. */}
			{(usageQuery.data?.byoPaused || usageQuery.data?.instanceFundedPaused) && (
				<div className="mb-6 space-y-3">
					{usageQuery.data.byoPaused && (
						<BudgetExhaustedAlert scope="own" verdict={usageQuery.data.byoBudgetVerdict} />
					)}
					{usageQuery.data.instanceFundedPaused && (
						<BudgetExhaustedAlert scope="shared" verdict={usageQuery.data.instanceBudgetVerdict} />
					)}
				</div>
			)}

			{isError ? (
				<QueryErrorAlert error={loadError} title="Couldn't load AI models" onRetry={handleRetry} />
			) : isLoading ? (
				<div className="flex h-40 items-center justify-center">
					<Spinner className="size-6" />
				</div>
			) : (
				<div className="space-y-6">
					<section className="space-y-4">
						<h2 className="text-lg font-semibold">Assignments</h2>
						{PURPOSES.map((meta) => (
							<AgentPurposeCard
								key={`${meta.purpose}:${bindingFor(meta.purpose)?.instanceModelId ?? bindingFor(meta.purpose)?.workspaceModelId ?? "none"}`}
								workspaceSlug={workspaceSlug}
								meta={meta}
								binding={bindingFor(meta.purpose)}
								availableModels={availableModels}
								featureEnabled={featureEnabled(meta.purpose)}
							/>
						))}
					</section>

					{aiSettingsQuery.data?.workspaceConnectionsAllowed && (
						<section className="space-y-4">
							<h2 className="text-lg font-semibold">Workspace providers</h2>
							<WorkspaceLlmProviderPanel
								workspaceSlug={workspaceSlug}
								workspaceConnectionsAllowed
							/>
						</section>
					)}
				</div>
			)}
		</div>
	);
}

interface AgentPurposeCardProps {
	workspaceSlug: string;
	meta: PurposeMeta;
	binding?: AgentBinding;
	availableModels: AvailableLlmModel[];
	featureEnabled: boolean;
}

function AgentPurposeCard({
	workspaceSlug,
	meta,
	binding,
	availableModels,
	featureEnabled,
}: AgentPurposeCardProps) {
	const queryClient = useQueryClient();
	const [selection, setSelection] = useState<ModelSelection | null>(bindingToSelection(binding));
	const [enabled, setEnabled] = useState(binding?.enabled ?? true);
	const [timeoutSeconds, setTimeoutSeconds] = useState(String(binding?.timeoutSeconds ?? 600));
	const [maxConcurrentJobs, setMaxConcurrentJobs] = useState(
		String(binding?.maxConcurrentJobs ?? 3),
	);
	const [allowInternet, setAllowInternet] = useState(binding?.allowInternet ?? false);
	const [showAdvanced, setShowAdvanced] = useState(false);
	// Withheld until the first submit, so nothing is marked invalid before anything was attempted.
	const [showErrors, setShowErrors] = useState(false);

	const invalidate = () =>
		queryClient.invalidateQueries({ queryKey: getBindingsQueryKey({ path: { workspaceSlug } }) });

	const upsert = useMutation({
		...upsertBindingMutation(),
		onSuccess: () => {
			invalidate();
			toast.success(`${meta.title} saved`);
		},
		onError: (error) => {
			toast.error(`Couldn't save ${meta.title.toLowerCase()}`, {
				description: problemDetailOf(error),
			});
		},
	});

	const remove = useMutation({
		...deleteBindingMutation(),
		onSuccess: () => {
			invalidate();
			toast.success(`${meta.title} turned off`);
		},
		onError: (error) => {
			toast.error(`Couldn't turn off ${meta.title.toLowerCase()}`, {
				description: problemDetailOf(error),
			});
		},
	});

	const noModels = availableModels.length === 0;
	const pending = upsert.isPending || remove.isPending;

	const timeout = parseWholeNumber(timeoutSeconds, MIN_TIMEOUT_SECONDS, "seconds");
	const concurrency = parseWholeNumber(maxConcurrentJobs, MIN_CONCURRENT_JOBS, "runs");
	const modelError = showErrors && !selection ? "Choose the model this runs on." : null;
	const timeoutError = showErrors ? timeout.error : null;
	const concurrencyError = showErrors ? concurrency.error : null;

	const modelHintId = `${meta.purpose}-model-hint`;
	const modelErrorId = `${meta.purpose}-model-error`;
	const modelDescribedBy =
		[noModels ? modelHintId : null, modelError ? modelErrorId : null].filter(Boolean).join(" ") ||
		undefined;

	const handleSubmit = (event: FormEvent) => {
		event.preventDefault();
		if (!selection || timeout.value == null || concurrency.value == null) {
			// Save stays enabled precisely so this reveals *why* the binding can't be saved — and the
			// disclosure opens, because the offending field may be inside it.
			setShowErrors(true);
			if (timeout.value == null || concurrency.value == null) setShowAdvanced(true);
			return;
		}
		upsert.mutate({
			path: { workspaceSlug, purpose: meta.purpose },
			body: {
				instanceModelId: selection.scope === "SHARED" ? selection.id : undefined,
				workspaceModelId: selection.scope === "WORKSPACE" ? selection.id : undefined,
				timeoutSeconds: timeout.value,
				maxConcurrentJobs: concurrency.value,
				allowInternet,
				enabled,
			},
		});
	};

	const handleTurnOff = () => {
		remove.mutate({ path: { workspaceSlug, purpose: meta.purpose } });
	};

	if (!featureEnabled) {
		return (
			<Card>
				<CardHeader>
					<CardTitle>{meta.title}</CardTitle>
					<CardDescription>
						This feature is turned off for the workspace. An instance admin enables it.
					</CardDescription>
				</CardHeader>
			</Card>
		);
	}

	return (
		<Card>
			<CardHeader>
				{/* `min-w-0` lets the description wrap instead of forcing the row wider than the card;
				    `flex-wrap` drops the badge onto its own line once there is no room for both. */}
				<div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
					<div className="min-w-0 flex-1">
						<CardTitle>{meta.title}</CardTitle>
						<CardDescription>{meta.description}</CardDescription>
					</div>
					{binding &&
						(binding.ready ? (
							<Badge variant="secondary">Ready</Badge>
						) : (
							<Badge variant="destructive">Not ready</Badge>
						))}
				</div>
			</CardHeader>
			{/* noValidate: this form validates itself so every rejection surfaces through `FieldError`.
			    Left to the browser, `min` would block submit with a native bubble the field can't explain. */}
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
									No models are available yet. Ask an instance admin to grant one, or add your own
									under Workspace providers.
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
											value={timeoutSeconds}
											aria-invalid={Boolean(timeoutError)}
											onChange={(e) => {
												setTimeoutSeconds(e.target.value);
												setShowErrors(false);
											}}
											disabled={pending}
										/>
										<FieldDescription>
											How long one run may take before it is abandoned.
										</FieldDescription>
										{timeoutError && <FieldError>{timeoutError}</FieldError>}
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
											onChange={(e) => {
												setMaxConcurrentJobs(e.target.value);
												setShowErrors(false);
											}}
											disabled={pending}
										/>
										<FieldDescription>
											How many runs this workspace may have in flight at once.
										</FieldDescription>
										{concurrencyError && <FieldError>{concurrencyError}</FieldError>}
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
								onClick={handleTurnOff}
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
