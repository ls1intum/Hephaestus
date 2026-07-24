import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { useState } from "react";
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
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
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

function bindingToSelection(binding?: AgentBinding): ModelSelection | null {
	if (binding?.instanceModelId != null) return { scope: "SHARED", id: binding.instanceModelId };
	if (binding?.workspaceModelId != null)
		return { scope: "WORKSPACE", id: binding.workspaceModelId };
	return null;
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

			{usageQuery.data?.usagePaused && (
				<div className="mb-6">
					<BudgetExhaustedAlert verdict={usageQuery.data.verdict} />
				</div>
			)}

			{isError ? (
				<Alert variant="destructive">
					<AlertCircle />
					<AlertTitle>Failed to load AI setup</AlertTitle>
					<AlertDescription>
						<p>The bindings, workspace policy, or the available model list could not be loaded.</p>
						<Button variant="outline" size="sm" className="mt-2" onClick={handleRetry}>
							Retry
						</Button>
					</AlertDescription>
				</Alert>
			) : isLoading ? (
				<div className="flex h-40 items-center justify-center">
					<Spinner className="h-6 w-6" />
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
	const [timeoutSeconds, setTimeoutSeconds] = useState(binding?.timeoutSeconds ?? 600);
	const [maxConcurrentJobs, setMaxConcurrentJobs] = useState(binding?.maxConcurrentJobs ?? 3);
	const [allowInternet, setAllowInternet] = useState(binding?.allowInternet ?? false);
	const [showAdvanced, setShowAdvanced] = useState(false);

	const invalidate = () =>
		queryClient.invalidateQueries({ queryKey: getBindingsQueryKey({ path: { workspaceSlug } }) });

	const upsert = useMutation({
		...upsertBindingMutation(),
		onSuccess: () => {
			invalidate();
			toast.success(`${meta.title} saved`);
		},
		onError: (error) => {
			toast.error(`Failed to save ${meta.title.toLowerCase()}`, {
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
			toast.error(`Failed to turn off ${meta.title.toLowerCase()}`, {
				description: problemDetailOf(error),
			});
		},
	});

	const noModels = availableModels.length === 0;
	const pending = upsert.isPending || remove.isPending;

	const handleSave = () => {
		if (!selection) return;
		upsert.mutate({
			path: { workspaceSlug, purpose: meta.purpose },
			body: {
				instanceModelId: selection.scope === "SHARED" ? selection.id : undefined,
				workspaceModelId: selection.scope === "WORKSPACE" ? selection.id : undefined,
				timeoutSeconds,
				maxConcurrentJobs,
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
					<CardTitle className="text-base">{meta.title}</CardTitle>
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
				<div className="flex items-start justify-between gap-4">
					<div>
						<CardTitle className="text-base">{meta.title}</CardTitle>
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
			<CardContent className="space-y-4">
				<Field>
					<FieldLabel htmlFor={`${meta.purpose}-model`}>{meta.title} runs on</FieldLabel>
					<ModelPicker
						id={`${meta.purpose}-model`}
						availableModels={availableModels}
						value={selection}
						onChange={setSelection}
						disabled={pending || noModels}
					/>
					{noModels && (
						<FieldDescription>
							No models are available yet. Ask an instance admin to grant one, or add your own under
							Workspace providers.
						</FieldDescription>
					)}
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

				<Button type="button" variant="ghost" size="sm" onClick={() => setShowAdvanced((v) => !v)}>
					{showAdvanced ? "Hide advanced" : "Advanced"}
				</Button>

				{showAdvanced && (
					<div className="space-y-3 rounded-lg border p-3">
						<Field>
							<FieldLabel htmlFor={`${meta.purpose}-timeout`}>Timeout (seconds)</FieldLabel>
							<Input
								id={`${meta.purpose}-timeout`}
								type="number"
								min={30}
								value={timeoutSeconds}
								onChange={(e) => setTimeoutSeconds(Number(e.target.value))}
								disabled={pending}
							/>
						</Field>
						<Field>
							<FieldLabel htmlFor={`${meta.purpose}-concurrency`}>Max concurrent runs</FieldLabel>
							<Input
								id={`${meta.purpose}-concurrency`}
								type="number"
								min={1}
								value={maxConcurrentJobs}
								onChange={(e) => setMaxConcurrentJobs(Number(e.target.value))}
								disabled={pending}
							/>
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
					</div>
				)}

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
					<Button type="button" size="sm" onClick={handleSave} disabled={pending || !selection}>
						Save
					</Button>
				</div>
			</CardContent>
		</Card>
	);
}
