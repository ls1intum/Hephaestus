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
	WorkspaceLlmConnection,
	WorkspaceLlmModel,
} from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
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
	workspaceConnectionsAllowed: boolean;
}

type TestResult = { ok: boolean; message: string };

/** Workspace-owned OpenAI-compatible connections and the models grouped under each connection. */
export function WorkspaceLlmProviderPanel({
	workspaceSlug,
	workspaceConnectionsAllowed,
}: WorkspaceLlmProviderPanelProps) {
	const queryClient = useQueryClient();
	const [connectionDialogOpen, setConnectionDialogOpen] = useState(false);
	const [editingConnection, setEditingConnection] = useState<WorkspaceLlmConnection | null>(null);
	const [modelDialogOpen, setModelDialogOpen] = useState(false);
	const [modelConnectionId, setModelConnectionId] = useState<number | null>(null);
	const [editingModel, setEditingModel] = useState<WorkspaceLlmModel | null>(null);
	const [mutatingModelId, setMutatingModelId] = useState<number | null>(null);
	const [registrationDisabled, setRegistrationDisabled] = useState(false);
	const [probingConnectionId, setProbingConnectionId] = useState<number | null>(null);
	const [testResults, setTestResults] = useState<Record<number, TestResult>>({});
	const [deletingConnection, setDeletingConnection] = useState<WorkspaceLlmConnection | null>(null);
	const registrationBlocked = !workspaceConnectionsAllowed || registrationDisabled;

	const connectionsQuery = useQuery(
		workspaceListLlmConnectionsOptions({ path: { workspaceSlug } }),
	);
	const connections = connectionsQuery.data ?? [];
	const modelsQuery = useQuery({
		...workspaceListLlmModelsOptions({ path: { workspaceSlug } }),
		enabled: connections.length > 0,
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

	const createConnection = useMutation({
		...workspaceCreateLlmConnectionMutation(),
		onSuccess: () => {
			invalidateConnections();
			setConnectionDialogOpen(false);
			toast.success("Provider connected");
		},
		onError: (error) => {
			if (problemStatusOf(error) === 403) setRegistrationDisabled(true);
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
		onError: (error) => toast.error(problemDetailOf(error, "Could not update your provider")),
	});
	const deleteConnection = useMutation({
		...workspaceDeleteLlmConnectionMutation(),
		onSuccess: () => {
			invalidateConnections();
			invalidateModels();
			toast.success("Provider disconnected");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not disconnect your provider")),
	});
	const probeConnection = useMutation({
		...workspaceProbeLlmConnectionMutation(),
		onSuccess: (result, variables) => {
			setTestResults((current) => ({
				...current,
				[variables.path.id]: result.reachable
					? {
							ok: true,
							message: `Connected — ${result.modelCount} model${result.modelCount === 1 ? "" : "s"} available`,
						}
					: { ok: false, message: result.message ?? "Could not reach the provider." },
			}));
		},
		onError: (error, variables) => {
			setTestResults((current) => ({
				...current,
				[variables.path.id]: {
					ok: false,
					message: problemDetailOf(error, "Could not reach the provider."),
				},
			}));
		},
		onSettled: () => setProbingConnectionId(null),
	});

	const createModel = useMutation({
		...workspaceCreateLlmModelMutation(),
		onSuccess: () => {
			invalidateModels();
			setModelDialogOpen(false);
			toast.success("Model added");
		},
		onError: (error) => {
			if (problemStatusOf(error) === 403) setRegistrationDisabled(true);
			toast.error(problemDetailOf(error, "Could not add the model"));
		},
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

	if (connectionsQuery.isError) {
		return (
			<QueryErrorAlert
				error={connectionsQuery.error}
				title="Could not load your AI providers"
				onRetry={() => connectionsQuery.refetch()}
			/>
		);
	}
	if (connectionsQuery.isLoading) {
		return (
			<div className="flex h-32 items-center justify-center">
				<Spinner className="size-6" />
			</div>
		);
	}
	if (connections.length > 0 && modelsQuery.isError) {
		return (
			<QueryErrorAlert
				error={modelsQuery.error}
				title="Could not load your provider models"
				onRetry={() => modelsQuery.refetch()}
			/>
		);
	}
	if (connections.length > 0 && modelsQuery.isLoading) {
		return (
			<div className="flex h-32 items-center justify-center">
				<Spinner className="size-6" />
			</div>
		);
	}

	const openCreateConnection = () => {
		setEditingConnection(null);
		setConnectionDialogOpen(true);
	};

	return (
		<div className="space-y-4">
			{registrationBlocked && (
				<Alert>
					<CircleAlert aria-hidden />
					<AlertTitle>New workspace providers and models are disabled</AlertTitle>
					<AlertDescription>
						An instance admin controls this setting. Existing providers and models remain
						manageable: you can test or edit connections and edit, disable, or delete models.
					</AlertDescription>
				</Alert>
			)}

			{connections.length === 0 ? (
				<Empty className="border border-dashed">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<Plug />
						</EmptyMedia>
						<EmptyTitle>Connect an OpenAI-compatible provider</EmptyTitle>
						<EmptyDescription>
							The API key is encrypted and used only for this workspace. Usage is billed by the
							provider account that owns the key.
						</EmptyDescription>
					</EmptyHeader>
					{!registrationBlocked && (
						<Button onClick={openCreateConnection}>
							<Plus className="size-4" aria-hidden /> Connect provider
						</Button>
					)}
				</Empty>
			) : (
				<>
					<div className="flex items-center justify-between gap-3">
						<div>
							<h3 className="text-sm font-medium">Your providers</h3>
							<p className="text-sm text-muted-foreground">
								OpenAI-compatible endpoints paid for with this workspace's credentials.
							</p>
						</div>
						{!registrationBlocked && (
							<Button size="sm" variant="outline" onClick={openCreateConnection}>
								<Plus className="size-4" aria-hidden /> Add provider
							</Button>
						)}
					</div>

					{connections.map((connection) => {
						const connectionModels = models.filter((model) => model.connectionId === connection.id);
						const testResult = testResults[connection.id];
						return (
							<Card key={connection.id}>
								<CardHeader>
									<div className="flex flex-wrap items-start justify-between gap-3">
										<div>
											<CardTitle>{connection.displayName}</CardTitle>
											<CardDescription>
												{connection.hasApiKey
													? `Credential configured · ends in ····${connection.apiKeyLast4 ?? "····"}`
													: "No API key stored"}
											</CardDescription>
										</div>
										<Badge variant={connection.enabled ? "default" : "secondary"}>
											{connection.enabled ? "Active" : "Off"}
										</Badge>
									</div>
								</CardHeader>
								<CardContent className="space-y-4">
									<div className="flex flex-wrap gap-2">
										<Button
											variant="outline"
											size="sm"
											onClick={() => {
												setEditingConnection(connection);
												setConnectionDialogOpen(true);
											}}
										>
											Edit
										</Button>
										<Button
											variant="outline"
											size="sm"
											disabled={probingConnectionId === connection.id}
											onClick={() => {
												setProbingConnectionId(connection.id);
												setTestResults((current) => {
													const next = { ...current };
													delete next[connection.id];
													return next;
												});
												probeConnection.mutate({ path: { workspaceSlug, id: connection.id } });
											}}
										>
											{probingConnectionId === connection.id ? "Testing…" : "Test connection"}
										</Button>
										<Button
											variant="outline"
											size="sm"
											className="text-destructive"
											disabled={deleteConnection.isPending}
											onClick={() => setDeletingConnection(connection)}
										>
											Disconnect
										</Button>
									</div>
									{testResult && (
										<Alert variant={testResult.ok ? "success" : "destructive"}>
											<AlertDescription>{testResult.message}</AlertDescription>
										</Alert>
									)}
									<div className="space-y-3">
										<div className="flex items-center justify-between">
											<h4 className="text-sm font-medium">Models</h4>
											{!registrationBlocked && (
												<Button
													size="sm"
													variant="outline"
													onClick={() => {
														setEditingModel(null);
														setModelConnectionId(connection.id);
														setModelDialogOpen(true);
													}}
												>
													<Plus className="size-4" aria-hidden /> Add model
												</Button>
											)}
										</div>
										<WorkspaceLlmModelsTable
											models={connectionModels}
											mutatingId={mutatingModelId}
											onEdit={(model) => {
												setEditingModel(model);
												setModelConnectionId(model.connectionId);
												setModelDialogOpen(true);
											}}
											onDelete={(model) => {
												setMutatingModelId(model.id);
												deleteModel.mutate({ path: { workspaceSlug, id: model.id } });
											}}
										/>
									</div>
								</CardContent>
							</Card>
						);
					})}
				</>
			)}

			<WorkspaceLlmConnectionFormDialog
				open={connectionDialogOpen}
				onOpenChange={setConnectionDialogOpen}
				editing={editingConnection}
				isSubmitting={createConnection.isPending || updateConnection.isPending}
				onCreate={(body: CreateWorkspaceLlmConnectionRequest) =>
					createConnection.mutate({ path: { workspaceSlug }, body })
				}
				onUpdate={(id, body: UpdateWorkspaceLlmConnectionRequest) =>
					updateConnection.mutate({ path: { workspaceSlug, id }, body })
				}
			/>
			<WorkspaceLlmModelFormDialog
				open={modelDialogOpen}
				onOpenChange={setModelDialogOpen}
				editing={editingModel}
				isSubmitting={createModel.isPending || updateModel.isPending}
				onCreate={(body: CreateWorkspaceLlmModelRequest) => {
					if (modelConnectionId == null) return;
					createModel.mutate({ path: { workspaceSlug, connectionId: modelConnectionId }, body });
				}}
				onUpdate={(id, body: UpdateWorkspaceLlmModelRequest) => {
					setMutatingModelId(id);
					updateModel.mutate({ path: { workspaceSlug, id }, body });
				}}
			/>

			<AlertDialog
				open={deletingConnection != null}
				onOpenChange={(open) => {
					if (!open && !deleteConnection.isPending) setDeletingConnection(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Disconnect “{deletingConnection?.displayName}”?</AlertDialogTitle>
						<AlertDialogDescription>
							The stored credential will be permanently removed. A connection with models still on
							it cannot be disconnected.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={deleteConnection.isPending}>Cancel</AlertDialogCancel>
						<AlertDialogAction
							variant="destructive"
							disabled={deleteConnection.isPending}
							onClick={() => {
								if (!deletingConnection) return;
								deleteConnection.mutate(
									{ path: { workspaceSlug, id: deletingConnection.id } },
									{ onSuccess: () => setDeletingConnection(null) },
								);
							}}
						>
							{deleteConnection.isPending ? "Disconnecting…" : "Disconnect provider"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
}
