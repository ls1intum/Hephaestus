import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute, retainSearchParams, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { toast } from "sonner";
import { z } from "zod";
import {
	cancelJobMutation,
	createConfigMutation,
	deleteConfigMutation,
	getConfigsOptions,
	getConfigsQueryKey,
	getJobOptions,
	listJobsOptions,
	listJobsQueryKey,
	retryDeliveryMutation,
	updateConfigMutation,
} from "@/api/@tanstack/react-query.gen";
import type { CreateAgentConfigRequest, UpdateAgentConfigRequest } from "@/api/types.gen";
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
	const queryClient = useQueryClient();
	const navigate = useNavigate({ from: "/w/$workspaceSlug/admin/agents" });
	const {
		workspaceSlug,
		isLoading: isWorkspaceLoading,
		error: workspaceError,
	} = useActiveWorkspaceSlug();
	const { practicesEnabled, isLoading: featuresLoading } = useWorkspaceFeatures();
	const { configId, jobId, page, status } = Route.useSearch();

	const configsQueryOptions = getConfigsOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
	});
	const configsQuery = useQuery({
		...configsQueryOptions,
		enabled: Boolean(workspaceSlug),
	});

	const jobsQueryOptions = listJobsOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
		query: {
			status: status === "ALL" ? undefined : status,
			configId: configId ? Number(configId) : undefined,
			page,
			size: pageSize,
		},
	});
	const jobsQuery = useQuery({
		...jobsQueryOptions,
		enabled: Boolean(workspaceSlug),
		placeholderData: (previousData) => previousData,
	});

	const jobDetailsQueryOptions = getJobOptions({
		path: { workspaceSlug: workspaceSlug ?? "", jobId: jobId ?? "pending" },
	});
	const jobDetailsQuery = useQuery({
		...jobDetailsQueryOptions,
		enabled: Boolean(workspaceSlug && jobId),
	});

	const invalidateConfigs = () => {
		if (!workspaceSlug) return;
		queryClient.invalidateQueries({
			queryKey: getConfigsQueryKey({ path: { workspaceSlug } }),
		});
	};

	const invalidateJobs = () => {
		if (!workspaceSlug) return;
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
			queryClient.invalidateQueries({
				queryKey: jobDetailsQueryOptions.queryKey,
			});
		}
	};

	const createConfig = useMutation({
		...createConfigMutation(),
		onSuccess: () => {
			invalidateConfigs();
			toast.success("Agent config created");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to create agent config");
		},
	});

	const updateConfig = useMutation({
		...updateConfigMutation(),
		onSuccess: () => {
			invalidateConfigs();
			toast.success("Agent config updated");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to update agent config");
		},
	});

	const deleteConfig = useMutation({
		...deleteConfigMutation(),
		onSuccess: () => {
			invalidateConfigs();
			toast.success("Agent config deleted");
		},
		onError: (error) => {
			toast.error((error as Error).message || "Failed to delete agent config");
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

	useEffect(() => {
		if (workspaceError) {
			toast.error(`Failed to resolve workspace: ${(workspaceError as Error).message}`);
		}
	}, [workspaceError]);

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	if (featuresLoading || !practicesEnabled) {
		return (
			<div className="flex h-64 items-center justify-center" role="status" aria-live="polite">
				<div className="flex items-center gap-3 text-muted-foreground">
					<Spinner className="size-8" />
					<span>
						{featuresLoading
							? "Loading review agent settings..."
							: "Practice review is not enabled for this workspace."}
					</span>
				</div>
			</div>
		);
	}

	type AgentsSearch = z.infer<typeof agentsSearchSchema>;

	const updateSearch = (
		next:
			| Partial<{
					status: (typeof jobStatusOptions)[number];
					configId: string;
					page: number;
					jobId: string | undefined;
			  }>
			| ((current: {
					status: (typeof jobStatusOptions)[number];
					configId: string;
					page: number;
					jobId?: string;
			  }) => Partial<{
					status: (typeof jobStatusOptions)[number];
					configId: string;
					page: number;
					jobId: string | undefined;
			  }>),
	) =>
		void navigate({
			replace: true,
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
			workspaceSlug={workspaceSlug ?? ""}
			configs={configsQuery.data ?? []}
			jobsPage={jobsQuery.data}
			selectedJob={jobDetailsQuery.data}
			selectedJobId={jobId ?? null}
			jobsFilter={{ status, configId, page, size: pageSize }}
			isLoadingConfigs={configsQuery.isLoading || isWorkspaceLoading || !workspaceSlug}
			isLoadingJobs={jobsQuery.isLoading || isWorkspaceLoading || !workspaceSlug}
			isLoadingJobDetails={jobDetailsQuery.isLoading}
			configsError={(configsQuery.error as Error | null) ?? null}
			jobsError={(jobsQuery.error as Error | null) ?? null}
			jobDetailsError={(jobDetailsQuery.error as Error | null) ?? null}
			isSavingConfig={createConfig.isPending || updateConfig.isPending}
			deletingConfigId={deleteConfig.variables?.path.configId ?? null}
			cancellingJobId={cancelJob.variables?.path.jobId ?? null}
			retryingJobId={retryDelivery.variables?.path.jobId ?? null}
			onRefresh={() => {
				invalidateConfigs();
				invalidateJobs();
			}}
			onCreateConfig={async (payload: CreateAgentConfigRequest) => {
				if (!workspaceSlug) return;
				await createConfig.mutateAsync({
					path: { workspaceSlug },
					body: payload,
				});
			}}
			onUpdateConfig={async (targetConfigId: number, payload: UpdateAgentConfigRequest) => {
				if (!workspaceSlug) return;
				await updateConfig.mutateAsync({
					path: { workspaceSlug, configId: targetConfigId },
					body: payload,
				});
			}}
			onDeleteConfig={async (targetConfigId: number) => {
				if (!workspaceSlug) return;
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
				updateSearch({ jobId: nextJobId ?? undefined });
			}}
			onCancelJob={async (jobId: string) => {
				if (!workspaceSlug) return;
				await cancelJob.mutateAsync({
					path: { workspaceSlug, jobId },
				});
			}}
			onRetryDelivery={async (jobId: string) => {
				if (!workspaceSlug) return;
				await retryDelivery.mutateAsync({
					path: { workspaceSlug, jobId },
				});
			}}
		/>
	);
}
