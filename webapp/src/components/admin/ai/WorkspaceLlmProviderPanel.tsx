import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CircleAlert, Plug, Plus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	workspaceCreateLlmConnectionMutation,
	workspaceCreateLlmModelMutation,
	workspaceDeleteLlmConnectionMutation,
	workspaceDeleteLlmModelMutation,
	workspaceListLlmConnectionsOptions,
	workspaceListLlmConnectionsQueryKey,
	workspaceListLlmModelsOptions,
	workspaceListLlmModelsQueryKey,
	workspaceProbeLlmConnectionMutation,
	workspaceUpdateLlmConnectionMutation,
	workspaceUpdateLlmModelMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	CreateWorkspaceLlmConnectionRequest,
	CreateWorkspaceLlmModelRequest,
	UpdateWorkspaceLlmConnectionRequest,
	UpdateWorkspaceLlmModelRequest,
	WorkspaceLlmModel,
} from "@/api/types.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Spinner } from "@/components/ui/spinner";
import { problemDetailOf, problemStatusOf } from "@/lib/problem-detail";
import { WorkspaceLlmConnectionFormDialog } from "./WorkspaceLlmConnectionFormDialog";
import { WorkspaceLlmModelFormDialog } from "./WorkspaceLlmModelFormDialog";
import { WorkspaceLlmModelsTable } from "./WorkspaceLlmModelsTable";

export interface WorkspaceLlmProviderPanelProps {
	workspaceSlug: string;
}

/**
 * "Your AI provider" — the workspace's own connected LLM provider and its models (#1368). Distinct
 * from the shared catalog: never shown to anyone outside this workspace, billed to whoever holds the
 * key, and its spend is never counted toward the org's monthly budget (see the usage screen).
 */
export function WorkspaceLlmProviderPanel({ workspaceSlug }: WorkspaceLlmProviderPanelProps) {
	const queryClient = useQueryClient();
	const [connectionDialogOpen, setConnectionDialogOpen] = useState(false);
	const [modelDialogOpen, setModelDialogOpen] = useState(false);
	const [editingModel, setEditingModel] = useState<WorkspaceLlmModel | null>(null);
	const [mutatingModelId, setMutatingModelId] = useState<number | null>(null);
	const [byoDisabled, setByoDisabled] = useState(false);
	const [testResult, setTestResult] = useState<{ ok: boolean; message: string } | null>(null);

	const connectionsQuery = useQuery(
		workspaceListLlmConnectionsOptions({ path: { workspaceSlug } }),
	);
	const connection = connectionsQuery.data?.[0];

	const modelsQuery = useQuery({
		...workspaceListLlmModelsOptions({ path: { workspaceSlug } }),
		enabled: connection != null,
	});
	const models = modelsQuery.data ?? [];

	const invalidateConnections = () =>
		queryClient.invalidateQueries({
			queryKey: workspaceListLlmConnectionsQueryKey({ path: { workspaceSlug } }),
		});
	const invalidateModels = () =>
		queryClient.invalidateQueries({
			queryKey: workspaceListLlmModelsQueryKey({ path: { workspaceSlug } }),
		});

	const handleByoForbidden = (error: unknown): boolean => {
		if (problemStatusOf(error) === 403) {
			setByoDisabled(true);
			return true;
		}
		return false;
	};

	const createConnection = useMutation({
		...workspaceCreateLlmConnectionMutation(),
		onSuccess: () => {
			invalidateConnections();
			setConnectionDialogOpen(false);
			toast.success("Provider connected");
		},
		onError: (error) => {
			if (handleByoForbidden(error)) return;
			toast.error(problemDetailOf(error, "Could not connect your provider"));
		},
	});

	const updateConnection = useMutation({
		...workspaceUpdateLlmConnectionMutation(),
		onSuccess: () => {
			invalidateConnections();
			setConnectionDialogOpen(false);
			toast.success("Provider updated");
		},
		onError: (error) => {
			if (handleByoForbidden(error)) return;
			toast.error(problemDetailOf(error, "Could not update your provider"));
		},
	});

	const deleteConnection = useMutation({
		...workspaceDeleteLlmConnectionMutation(),
		onSuccess: () => {
			invalidateConnections();
			invalidateModels();
			toast.success("Provider disconnected");
		},
		onError: (error) => {
			toast.error(problemDetailOf(error, "Could not disconnect your provider"));
		},
	});

	const probeConnection = useMutation({
		...workspaceProbeLlmConnectionMutation(),
		onSuccess: (result) => {
			if (result.reachable) {
				setTestResult({
					ok: true,
					message: `Connected — ${result.modelCount} model${result.modelCount === 1 ? "" : "s"} available`,
				});
			} else {
				setTestResult({ ok: false, message: result.message ?? "Could not reach the provider." });
			}
		},
		onError: (error) => {
			if (handleByoForbidden(error)) return;
			setTestResult({
				ok: false,
				message: problemDetailOf(error, "Could not reach the provider."),
			});
		},
	});

	const createModel = useMutation({
		...workspaceCreateLlmModelMutation(),
		onSuccess: () => {
			invalidateModels();
			setModelDialogOpen(false);
			toast.success("Model added");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not add the model")),
	});

	const updateModel = useMutation({
		...workspaceUpdateLlmModelMutation(),
		onSuccess: () => {
			invalidateModels();
			setModelDialogOpen(false);
			toast.success("Model updated");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not update the model")),
		onSettled: () => setMutatingModelId(null),
	});

	const deleteModel = useMutation({
		...workspaceDeleteLlmModelMutation(),
		onSuccess: () => {
			invalidateModels();
			toast.success("Model deleted");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not delete the model")),
		onSettled: () => setMutatingModelId(null),
	});

	if (byoDisabled) {
		return (
			<Alert>
				<CircleAlert aria-hidden />
				<AlertTitle>Your own AI provider isn't available</AlertTitle>
				<AlertDescription>
					Connecting your own AI provider is disabled on this server.
				</AlertDescription>
			</Alert>
		);
	}

	if (!connectionsQuery.isLoading && !connection) {
		return (
			<>
				<Empty className="border border-dashed">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<Plug />
						</EmptyMedia>
						<EmptyTitle>Connect your own AI provider</EmptyTitle>
						<EmptyDescription>
							Shared models are set up and paid for by your organization. You can also connect your
							own AI provider with your own API key — it's used only in this workspace and billed
							directly to you.
						</EmptyDescription>
					</EmptyHeader>
					<Button onClick={() => setConnectionDialogOpen(true)}>
						<Plus className="size-4" aria-hidden />
						Connect your own AI provider
					</Button>
				</Empty>

				<WorkspaceLlmConnectionFormDialog
					open={connectionDialogOpen}
					onOpenChange={setConnectionDialogOpen}
					editing={null}
					isSubmitting={createConnection.isPending}
					onCreate={(body: CreateWorkspaceLlmConnectionRequest) =>
						createConnection.mutate({ path: { workspaceSlug }, body })
					}
					onUpdate={() => {}}
				/>
			</>
		);
	}

	if (connectionsQuery.isLoading || !connection) {
		return (
			<div className="flex h-32 items-center justify-center">
				<Spinner className="size-6" />
			</div>
		);
	}

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<div className="flex flex-wrap items-start justify-between gap-3">
						<div>
							<CardTitle>{connection.displayName}</CardTitle>
							<CardDescription>
								{connection.hasApiKey
									? `Configured · ends in ····${connection.apiKeyLast4 ?? "····"}`
									: "No API key stored"}
							</CardDescription>
						</div>
						<Badge variant={connection.enabled ? "default" : "secondary"}>
							{connection.enabled ? "Active" : "Off — existing settings stop working"}
						</Badge>
					</div>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="flex flex-wrap gap-2">
						<Button variant="outline" size="sm" onClick={() => setConnectionDialogOpen(true)}>
							Edit
						</Button>
						<Button
							variant="outline"
							size="sm"
							disabled={probeConnection.isPending}
							onClick={() => {
								setTestResult(null);
								probeConnection.mutate({ path: { workspaceSlug, id: connection.id } });
							}}
						>
							{probeConnection.isPending ? "Testing…" : "Test connection"}
						</Button>
						<Button
							variant="outline"
							size="sm"
							className="text-destructive"
							disabled={deleteConnection.isPending}
							onClick={() =>
								deleteConnection.mutate({ path: { workspaceSlug, id: connection.id } })
							}
						>
							Disconnect
						</Button>
					</div>
					{testResult && (
						<Alert variant={testResult.ok ? "success" : "destructive"}>
							<AlertDescription>{testResult.message}</AlertDescription>
						</Alert>
					)}
				</CardContent>
			</Card>

			<div className="space-y-3">
				<div className="flex items-center justify-between">
					<h3 className="text-sm font-medium">Models</h3>
					<Button
						size="sm"
						variant="outline"
						onClick={() => {
							setEditingModel(null);
							setModelDialogOpen(true);
						}}
					>
						<Plus className="size-4" aria-hidden />
						Add model
					</Button>
				</div>
				<WorkspaceLlmModelsTable
					models={models}
					mutatingId={mutatingModelId}
					onEdit={(model) => {
						setEditingModel(model);
						setModelDialogOpen(true);
					}}
					onDelete={(model) => {
						setMutatingModelId(model.id);
						deleteModel.mutate({ path: { workspaceSlug, id: model.id } });
					}}
				/>
			</div>

			<WorkspaceLlmConnectionFormDialog
				open={connectionDialogOpen}
				onOpenChange={setConnectionDialogOpen}
				editing={connection}
				isSubmitting={updateConnection.isPending}
				onCreate={() => {}}
				onUpdate={(id, body: UpdateWorkspaceLlmConnectionRequest) =>
					updateConnection.mutate({ path: { workspaceSlug, id }, body })
				}
			/>

			<WorkspaceLlmModelFormDialog
				open={modelDialogOpen}
				onOpenChange={setModelDialogOpen}
				editing={editingModel}
				isSubmitting={createModel.isPending || updateModel.isPending}
				onCreate={(body: CreateWorkspaceLlmModelRequest) =>
					createModel.mutate({ path: { workspaceSlug, connectionId: connection.id }, body })
				}
				onUpdate={(id, body: UpdateWorkspaceLlmModelRequest) => {
					setMutatingModelId(id);
					updateModel.mutate({ path: { workspaceSlug, id }, body });
				}}
			/>
		</div>
	);
}
