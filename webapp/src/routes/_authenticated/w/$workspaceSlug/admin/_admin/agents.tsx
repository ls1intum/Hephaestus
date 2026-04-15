import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Navigate, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { toast } from "sonner";
import { z } from "zod";
import {
	cancelJobMutation,
	createConfigMutation,
	createRunnerMutation,
	deleteConfigMutation,
	deleteRunnerMutation,
	getConfigsOptions,
	getConfigsQueryKey,
	getJobOptions,
	getRunnersOptions,
	getRunnersQueryKey,
	listJobsOptions,
	listJobsQueryKey,
	retryDeliveryMutation,
	updateConfigMutation,
	updateRunnerMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	AgentConfig,
	AgentRunner,
	CreateAgentConfigRequest,
	CreateAgentRunnerRequest,
	UpdateAgentConfigRequest,
	UpdateAgentRunnerRequest,
} from "@/api/types.gen";
import { AdminAgentsPage } from "@/components/admin/agents/AdminAgentsPage";
import { Spinner } from "@/components/ui/spinner";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

const pageSize = 10;
const jobStatusOptions = [
	"ALL",
	"QUEUED",
	"RUNNING",
	"COMPLETED",
	"FAILED",
	"TIMED_OUT",
	"CANCELLED",
] as const;

const agentsSearchSchema = z.object({
	configId: z.string().default(""),
	jobId: z.string().optional(),
	page: z.coerce.number().int().min(0).default(0),
	status: z.enum(jobStatusOptions).default("ALL"),
});

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/agents")({
	component: AdminAgentsContainer,
	validateSearch: agentsSearchSchema,
	search: {
		middlewares: [retainSearchParams(["status", "configId", "page", "jobId"])],
	},
});

function AdminAgentsContainer() {
	const {
		workspaceSlug,
		isLoading: isWorkspaceLoading,
		error: workspaceError,
	} = useActiveWorkspaceSlug();
	const { practicesEnabled, isLoading: featuresLoading } = useWorkspaceFeatures();

	useEffect(() => {
		if (workspaceError) {
			toast.error(`Failed to resolve workspace: ${(workspaceError as Error).message}`);
		}
	}, [workspaceError]);

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	if (!featuresLoading && !practicesEnabled && workspaceSlug) {
		return <Navigate to="/w/$workspaceSlug/admin/settings" params={{ workspaceSlug }} replace />;
	}

	if (featuresLoading || !practicesEnabled) {
		return (
			<div className="flex h-64 items-center justify-center" role="status" aria-live="polite">
				<div className="flex items-center gap-3 text-muted-foreground">
					<Spinner className="size-8" />
					<span>Loading review agent settings...</span>
				</div>
			</div>
		);
	}

	if (!workspaceSlug) {
		return null;
	}

	return (
		<AdminAgentsContent workspaceSlug={workspaceSlug} isWorkspaceLoading={isWorkspaceLoading} />
	);
}

interface AdminAgentsContentProps {
	workspaceSlug: string;
	isWorkspaceLoading: boolean;
}

function AdminAgentsContent({ workspaceSlug, isWorkspaceLoading }: AdminAgentsContentProps) {
	const queryClient = useQueryClient();
	const navigate = useNavigate({ from: Route.fullPath });
	const { configId, jobId, page, status } = Route.useSearch();

	const configsQueryOptions = getConfigsOptions({
		path: { workspaceSlug },
	});
	const configsQuery = useQuery(configsQueryOptions);

	const runnersQueryOptions = getRunnersOptions({
		path: { workspaceSlug },
	});
	const runnersQuery = useQuery(runnersQueryOptions);

	const jobsQueryOptions = listJobsOptions({
		path: { workspaceSlug },
		query: {
			status: status === "ALL" ? undefined : status,
			configId: configId ? Number(configId) : undefined,
			page,
			size: pageSize,
		},
	});
	const jobsQuery = useQuery({
		...jobsQueryOptions,
		placeholderData: (previousData) => previousData,
	});

	const jobDetailsQueryOptions = getJobOptions({
		path: { workspaceSlug, jobId: jobId ?? "pending" },
	});
	const jobDetailsQuery = useQuery({
		...jobDetailsQueryOptions,
		enabled: jobId !== undefined,
	});

	const invalidateConfigs = () => {
		queryClient.invalidateQueries({
			queryKey: getConfigsQueryKey({ path: { workspaceSlug } }),
		});
	};

	const configsQueryKey = getConfigsQueryKey({ path: { workspaceSlug } });
	const runnersQueryKey = getRunnersQueryKey({ path: { workspaceSlug } });

	const upsertById = <T extends { id: number; name: string }>(
		items: T[] | undefined,
		item: T,
	): T[] => {
		const currentItems = items ?? [];
		const existingIndex = currentItems.findIndex((currentItem) => currentItem.id === item.id);
		if (existingIndex === -1) {
			return [...currentItems, item].sort((left, right) => left.name.localeCompare(right.name));
		}

		return currentItems.map((currentItem) => (currentItem.id === item.id ? item : currentItem));
	};

	const removeById = <T extends { id: number }>(items: T[] | undefined, itemId: number): T[] =>
		(items ?? []).filter((item) => item.id !== itemId);

	const invalidateRunners = () => {
		queryClient.invalidateQueries({
			queryKey: runnersQueryKey,
		});
	};

	const invalidateJobs = () => {
		queryClient.invalidateQueries({
			queryKey: listJobsQueryKey({
				path: { workspaceSlug },
				query: {
					status: status === "ALL" ? undefined : status,
					configId: configId ? Number(configId) : undefined,
					page,
					size: pageSize,
				},
			}),
		});
		if (jobId) {
			queryClient.invalidateQueries({ queryKey: jobDetailsQueryOptions.queryKey });
		}
	};

	const createConfig = useMutation({
		...createConfigMutation(),
		onSuccess: (config) => {
			queryClient.setQueryData<AgentConfig[]>(configsQueryKey, (current) =>
				upsertById(current, config),
			);
			invalidateConfigs();
			toast.success("Agent config saved");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to create agent config");
		},
	});

	const updateConfig = useMutation({
		...updateConfigMutation(),
		onSuccess: (config) => {
			queryClient.setQueryData<AgentConfig[]>(configsQueryKey, (current) =>
				upsertById(current, config),
			);
			invalidateConfigs();
			toast.success("Agent config updated");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to update agent config");
		},
	});

	const deleteConfig = useMutation({
		...deleteConfigMutation(),
		onSuccess: (_, variables) => {
			queryClient.setQueryData<AgentConfig[]>(configsQueryKey, (current) =>
				removeById(current, variables.path.configId),
			);
			invalidateConfigs();
			toast.success("Agent config deleted");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to delete agent config");
		},
	});

	const createRunner = useMutation({
		...createRunnerMutation(),
		onSuccess: (runner) => {
			queryClient.setQueryData<AgentRunner[]>(runnersQueryKey, (current) =>
				upsertById(current, runner),
			);
			invalidateRunners();
			toast.success("Runner saved");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to create runner");
		},
	});

	const updateRunner = useMutation({
		...updateRunnerMutation(),
		onSuccess: (runner) => {
			queryClient.setQueryData<AgentRunner[]>(runnersQueryKey, (current) =>
				upsertById(current, runner),
			);
			invalidateRunners();
			invalidateConfigs();
			invalidateJobs();
			toast.success("Runner updated");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to update runner");
		},
	});

	const deleteRunner = useMutation({
		...deleteRunnerMutation(),
		onSuccess: (_, variables) => {
			queryClient.setQueryData<AgentRunner[]>(runnersQueryKey, (current) =>
				removeById(current, variables.path.runnerId),
			);
			invalidateRunners();
			invalidateConfigs();
			toast.success("Runner deleted");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to delete runner");
		},
	});

	const cancelJob = useMutation({
		...cancelJobMutation(),
		onSuccess: () => {
			invalidateJobs();
			toast.success("Job cancelled");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to cancel job");
		},
	});

	const retryDelivery = useMutation({
		...retryDeliveryMutation(),
		onSuccess: () => {
			invalidateJobs();
			toast.success("Delivery retry queued");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to retry delivery");
		},
	});

	type AgentsSearch = z.infer<typeof agentsSearchSchema>;

	const updateSearch = (
		next:
			| Partial<{
					status: (typeof jobStatusOptions)[number];
					configId: string;
					page: number;
					jobId: string | undefined;
			  }>
			| ((current: AgentsSearch) => Partial<{
					status: (typeof jobStatusOptions)[number];
					configId: string;
					page: number;
					jobId: string | undefined;
			  }>),
		options?: { replace?: boolean },
	) =>
		void navigate({
			replace: options?.replace ?? true,
			search: (current: AgentsSearch) => {
				const resolved = typeof next === "function" ? next(current) : next;
				return {
					...current,
					...resolved,
				};
			},
		});

	return (
		<AdminAgentsPage
			runners={runnersQuery.data ?? []}
			configs={configsQuery.data ?? []}
			jobsPage={jobsQuery.data}
			selectedJob={jobDetailsQuery.data}
			selectedJobId={jobId ?? null}
			jobsFilter={{ status, configId, page, size: pageSize }}
			isLoadingRunners={runnersQuery.isLoading || isWorkspaceLoading}
			isLoadingConfigs={configsQuery.isLoading || isWorkspaceLoading}
			isLoadingJobs={jobsQuery.isLoading || isWorkspaceLoading}
			isLoadingJobDetails={jobDetailsQuery.isLoading}
			jobsError={(jobsQuery.error as Error | null) ?? null}
			jobDetailsError={(jobDetailsQuery.error as Error | null) ?? null}
			isSavingRunner={createRunner.isPending || updateRunner.isPending}
			isSavingConfig={createConfig.isPending || updateConfig.isPending}
			deletingRunnerId={deleteRunner.variables?.path.runnerId ?? null}
			deletingConfigId={deleteConfig.variables?.path.configId ?? null}
			cancellingJobId={cancelJob.variables?.path.jobId ?? null}
			retryingJobId={retryDelivery.variables?.path.jobId ?? null}
			onRefresh={() => {
				invalidateRunners();
				invalidateConfigs();
				invalidateJobs();
			}}
			onCreateRunner={async (payload: CreateAgentRunnerRequest) => {
				return await createRunner.mutateAsync({
					path: { workspaceSlug },
					body: payload,
				});
			}}
			onUpdateRunner={async (targetRunnerId: number, payload: UpdateAgentRunnerRequest) => {
				return await updateRunner.mutateAsync({
					path: { workspaceSlug, runnerId: targetRunnerId },
					body: payload,
				});
			}}
			onDeleteRunner={async (targetRunnerId: number) => {
				await deleteRunner.mutateAsync({
					path: { workspaceSlug, runnerId: targetRunnerId },
				});
			}}
			onCreateConfig={async (payload: CreateAgentConfigRequest) => {
				return await createConfig.mutateAsync({
					path: { workspaceSlug },
					body: payload,
				});
			}}
			onUpdateConfig={async (targetConfigId: number, payload: UpdateAgentConfigRequest) => {
				return await updateConfig.mutateAsync({
					path: { workspaceSlug, configId: targetConfigId },
					body: payload,
				});
			}}
			onDeleteConfig={async (targetConfigId: number) => {
				await deleteConfig.mutateAsync({
					path: { workspaceSlug, configId: targetConfigId },
				});
			}}
			onChangeJobsFilter={(next) => {
				updateSearch((current) => ({
					configId: next.configId ?? current.configId,
					jobId: undefined,
					page: next.page ?? 0,
					status: next.status ?? current.status,
				}));
			}}
			onSelectJob={(nextJobId) => {
				updateSearch({ jobId: nextJobId ?? undefined }, { replace: nextJobId == null });
			}}
			onCancelJob={async (targetJobId: string) => {
				await cancelJob.mutateAsync({ path: { workspaceSlug, jobId: targetJobId } });
			}}
			onRetryDelivery={async (targetJobId: string) => {
				await retryDelivery.mutateAsync({ path: { workspaceSlug, jobId: targetJobId } });
			}}
		/>
	);
}
