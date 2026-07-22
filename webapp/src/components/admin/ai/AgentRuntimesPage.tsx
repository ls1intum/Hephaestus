import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle, Bot, Plus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	createConfigMutation,
	deleteConfigMutation,
	getAiSettingsOptions,
	getAiSettingsQueryKey,
	getConfigsOptions,
	getConfigsQueryKey,
	getLlmUsageReportOptions,
	updateConfigMutation,
	updateMentorConfigMutation,
	workspaceListAvailableLlmModelsOptions,
} from "@/api/@tanstack/react-query.gen";
import type {
	AgentConfig,
	AvailableLlmModel,
	CreateAgentConfigRequest,
	UpdateAgentConfigRequest,
} from "@/api/types.gen";
import { currentMonthUtc } from "@/components/admin/usage/usageUtils";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AgentConfigCard } from "./AgentConfigCard";
import { AgentConfigForm } from "./AgentConfigForm";
import { BudgetExhaustedAlert } from "./BudgetExhaustedAlert";
import { deriveDesignations } from "./utils";
import { WorkspaceLlmProviderPanel } from "./WorkspaceLlmProviderPanel";

const NEW_RUNTIME = "__new__";
const MENTOR_UNCONFIGURED = "__none__";

interface AgentRuntimesPageProps {
	workspaceSlug: string;
}

export function AgentRuntimesPage({ workspaceSlug }: AgentRuntimesPageProps) {
	const queryClient = useQueryClient();
	// null = "New runtime" form; number = editing that config id.
	const [selectedId, setSelectedId] = useState<number | null>(null);

	const configsQuery = useQuery({
		...getConfigsOptions({ path: { workspaceSlug } }),
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
	// Only feeds the usagePaused banner below, not a full usage breakdown (that's the usage page), so
	// a longer staleTime than the app default is fine — a minute-old verdict is still an accurate
	// "why did detection stop" signal.
	const usageQuery = useQuery({
		...getLlmUsageReportOptions({ path: { workspaceSlug }, query: { month: currentMonthUtc() } }),
		enabled: Boolean(workspaceSlug),
		staleTime: 60_000,
	});

	const configs = configsQuery.data ?? [];
	const availableModels: AvailableLlmModel[] = availableModelsQuery.data ?? [];
	const designations = deriveDesignations(aiSettingsQuery.data);
	const selectedConfig = selectedId != null ? configs.find((c) => c.id === selectedId) : undefined;

	// Resolve a bound config's display name from the available-models list — never the raw upstream
	// model id or the owning connection (#1368 glossary rule #3). Legacy (unbound) configs fall back to
	// `AgentConfigCard`'s own provider/model-name rendering.
	const modelLabelFor = (config: AgentConfig): string | undefined => {
		const boundId = config.instanceModelId ?? config.workspaceModelId;
		if (boundId == null) return undefined;
		const scope = config.instanceModelId != null ? "SHARED" : "WORKSPACE";
		return availableModels.find((m) => m.scope === scope && m.id === boundId)?.displayName;
	};

	const invalidateAll = () => {
		queryClient.invalidateQueries({
			queryKey: getConfigsQueryKey({ path: { workspaceSlug } }),
		});
		queryClient.invalidateQueries({
			queryKey: getAiSettingsQueryKey({ path: { workspaceSlug } }),
		});
	};

	const createConfig = useMutation({
		...createConfigMutation(),
		onSuccess: (created) => {
			invalidateAll();
			toast.success("Agent configuration created");
			setSelectedId(created.id);
		},
		onError: (error) => {
			toast.error("Failed to create the agent configuration", {
				description: error instanceof Error ? error.message : undefined,
			});
		},
	});

	const updateConfig = useMutation({
		...updateConfigMutation(),
		onSuccess: () => {
			invalidateAll();
			toast.success("Agent configuration updated");
		},
		onError: (error) => {
			toast.error("Failed to update the agent configuration", {
				description: error instanceof Error ? error.message : undefined,
			});
		},
	});

	const deleteConfig = useMutation({
		...deleteConfigMutation(),
		onSuccess: (_data, variables) => {
			invalidateAll();
			toast.success("Agent configuration deleted");
			if (variables.path.configId === selectedId) {
				setSelectedId(null);
			}
		},
		onError: (error) => {
			// A 409 means the config is bound to a workspace feature; surface the server message.
			toast.error("Failed to delete the agent configuration", {
				description:
					error instanceof Error
						? error.message
						: "It may still be bound to practice detection or the mentor.",
			});
		},
	});

	const updateMentorConfig = useMutation({
		...updateMentorConfigMutation(),
		onSuccess: () => {
			// The "Model for Mentor" badge derives from ai-settings, so invalidate it after a mentor-config change.
			invalidateAll();
			toast.success("Mentor model updated");
		},
		onError: (error) => {
			toast.error("Failed to update mentor model", {
				description: error instanceof Error ? error.message : undefined,
			});
		},
	});

	const handleCreate = (body: CreateAgentConfigRequest) => {
		createConfig.mutate({ path: { workspaceSlug }, body });
	};

	const handleUpdate = (body: UpdateAgentConfigRequest) => {
		if (selectedId == null) return;
		updateConfig.mutate({ path: { workspaceSlug, configId: selectedId }, body });
	};

	const handleDelete = (config: AgentConfig) => {
		deleteConfig.mutate({ path: { workspaceSlug, configId: config.id } });
	};

	const handleBindMentor = (value: string) => {
		updateMentorConfig.mutate({
			path: { workspaceSlug },
			body: { configId: value === MENTOR_UNCONFIGURED ? undefined : Number(value) },
		});
	};

	const isLoading =
		configsQuery.isLoading || aiSettingsQuery.isLoading || availableModelsQuery.isLoading;
	const isError = configsQuery.isError || aiSettingsQuery.isError || availableModelsQuery.isError;
	const formPending = createConfig.isPending || updateConfig.isPending;

	const handleRetry = () => {
		configsQuery.refetch();
		aiSettingsQuery.refetch();
		availableModelsQuery.refetch();
	};
	const mentorEnabled = aiSettingsQuery.data?.mentorEnabled ?? false;
	const mentorConfigId = aiSettingsQuery.data?.mentorConfigId;
	const isAvailableMentorConfig = (config: AgentConfig) => {
		if (!config.enabled) return false;
		const modelId = config.instanceModelId ?? config.workspaceModelId;
		if (modelId == null) return false;
		const scope = config.instanceModelId != null ? "SHARED" : "WORKSPACE";
		return availableModels.some((model) => model.scope === scope && model.id === modelId);
	};
	const selectableMentorConfigs = configs.filter(isAvailableMentorConfig);
	const selectedMentorConfig = configs.find((config) => config.id === mentorConfigId);
	const mentorBindingUnavailable =
		mentorConfigId != null &&
		(selectedMentorConfig == null || !isAvailableMentorConfig(selectedMentorConfig));
	const mentorRuntimeItems = [
		{ value: MENTOR_UNCONFIGURED, label: "Not configured", disabled: false },
		...selectableMentorConfigs.map((config) => ({
			value: String(config.id),
			label: config.name,
			disabled: false,
		})),
		...(mentorBindingUnavailable
			? [
					{
						value: String(mentorConfigId),
						label: `${selectedMentorConfig?.name ?? `Configuration #${mentorConfigId}`} (unavailable)`,
						disabled: true,
					},
				]
			: []),
	];

	return (
		<div className="container mx-auto max-w-6xl py-6">
			<div className="mb-6">
				<h1 className="text-3xl font-bold tracking-tight">AI setup</h1>
				<p className="text-muted-foreground">
					Choose which models power practice reviews and the mentor, or connect workspace-funded
					providers.
				</p>
			</div>

			{usageQuery.data?.usagePaused && (
				<div className="mb-6">
					<BudgetExhaustedAlert verdict={usageQuery.data.verdict} />
				</div>
			)}

			<Tabs defaultValue="models">
				<TabsList>
					<TabsTrigger value="models">Configurations</TabsTrigger>
					<TabsTrigger value="provider">Workspace providers</TabsTrigger>
				</TabsList>

				<TabsContent value="models" className="mt-6">
					<div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
						<div className="space-y-3">
							<div className="flex items-center justify-end">
								<Button
									size="sm"
									variant={selectedId === null ? "default" : "outline"}
									onClick={() => setSelectedId(null)}
								>
									<Plus className="mr-1.5 h-4 w-4" />
									New configuration
								</Button>
							</div>

							{isError ? (
								<Alert variant="destructive">
									<AlertCircle />
									<AlertTitle>Failed to load AI setup</AlertTitle>
									<AlertDescription>
										<p>
											Configurations, workspace policy, or the available model list could not be
											loaded.
										</p>
										<Button variant="outline" size="sm" className="mt-2" onClick={handleRetry}>
											Retry
										</Button>
									</AlertDescription>
								</Alert>
							) : isLoading ? (
								<div className="flex h-40 items-center justify-center">
									<Spinner className="h-6 w-6" />
								</div>
							) : configs.length === 0 ? (
								<Empty className="border border-dashed">
									<EmptyHeader>
										<EmptyMedia variant="icon">
											<Bot />
										</EmptyMedia>
										<EmptyTitle>No agent configurations yet</EmptyTitle>
										<EmptyDescription>
											Choose a model for your first agent configuration to start running practice
											reviews.
										</EmptyDescription>
									</EmptyHeader>
								</Empty>
							) : (
								configs.map((config) => (
									<AgentConfigCard
										key={config.id}
										config={config}
										modelLabel={modelLabelFor(config)}
										designation={designations.get(config.id)}
										selected={config.id === selectedId}
										isDeleting={
											deleteConfig.isPending && deleteConfig.variables?.path.configId === config.id
										}
										onEdit={(c) => setSelectedId(c.id)}
										onDelete={handleDelete}
									/>
								))
							)}

							{mentorEnabled && (
								<Card className="mt-4">
									<CardHeader>
										<CardTitle className="text-sm">Mentor model</CardTitle>
									</CardHeader>
									<CardContent className="space-y-2">
										<Select
											items={mentorRuntimeItems}
											value={mentorConfigId != null ? String(mentorConfigId) : MENTOR_UNCONFIGURED}
											disabled={updateMentorConfig.isPending}
											onValueChange={(value) => {
												if (value) handleBindMentor(value);
											}}
										>
											<SelectTrigger>
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												{mentorRuntimeItems.map((item) => (
													<SelectItem key={item.value} value={item.value} disabled={item.disabled}>
														{item.label}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
										{mentorBindingUnavailable && (
											<Alert variant="destructive">
												<AlertCircle />
												<AlertDescription>
													The selected configuration cannot run. Choose an available configuration.
												</AlertDescription>
											</Alert>
										)}
										<p className="text-xs text-muted-foreground">
											The model that powers the mentor chat for this workspace.
										</p>
									</CardContent>
								</Card>
							)}
						</div>

						{!isError && !isLoading && (
							<Card>
								<CardHeader>
									<CardTitle className="text-base">
										{selectedConfig ? `Edit: ${selectedConfig.name}` : "New agent configuration"}
									</CardTitle>
								</CardHeader>
								<CardContent>
									<AgentConfigForm
										key={selectedConfig?.id ?? NEW_RUNTIME}
										config={selectedConfig}
										availableModels={availableModels}
										isPending={formPending}
										onCreate={handleCreate}
										onUpdate={handleUpdate}
										onCancel={selectedConfig ? () => setSelectedId(null) : undefined}
									/>
								</CardContent>
							</Card>
						)}
					</div>
				</TabsContent>

				<TabsContent value="provider" className="mt-6">
					{aiSettingsQuery.isError ? (
						<Alert variant="destructive">
							<AlertCircle aria-hidden />
							<AlertTitle>Could not load provider policy</AlertTitle>
							<AlertDescription>
								The workspace provider policy could not be loaded. Retry before making changes.
								<Button
									variant="outline"
									size="sm"
									className="mt-2"
									onClick={() => aiSettingsQuery.refetch()}
								>
									Retry
								</Button>
							</AlertDescription>
						</Alert>
					) : aiSettingsQuery.isLoading ? (
						<div className="flex h-32 items-center justify-center">
							<Spinner className="size-6" />
						</div>
					) : (
						<WorkspaceLlmProviderPanel
							workspaceSlug={workspaceSlug}
							workspaceConnectionsAllowed={
								aiSettingsQuery.data?.workspaceConnectionsAllowed ?? false
							}
						/>
					)}
				</TabsContent>
			</Tabs>
		</div>
	);
}
