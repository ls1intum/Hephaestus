import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { AlertCircle, BrainCircuit, Plus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	adminCreateLlmConnectionMutation,
	adminCreateLlmModelMutation,
	adminDeleteLlmConnectionMutation,
	adminDeleteLlmModelMutation,
	adminGetLlmSettingsOptions,
	adminGetLlmSettingsQueryKey,
	adminListLlmConnectionsOptions,
	adminListLlmConnectionsQueryKey,
	adminListLlmModelsOptions,
	adminListLlmModelsQueryKey,
	adminListWorkspacesOptions,
	adminProbeLlmConnectionDraftMutation,
	adminProbeLlmConnectionMutation,
	adminUpdateLlmConnectionMutation,
	adminUpdateLlmModelMutation,
	adminUpdateLlmModelPriceMutation,
	adminUpdateLlmModelSharingMutation,
	adminUpdateLlmSettingsMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	CreateLlmConnectionRequest,
	LlmConnection,
	LlmModel,
	UpdateInstanceLlmSettingsRequest,
	UpdateLlmConnectionRequest,
} from "@/api/types.gen";
import { AdminLlmConnectionFormDialog } from "@/components/admin/llm/AdminLlmConnectionFormDialog";
import { AdminLlmConnectionsTable } from "@/components/admin/llm/AdminLlmConnectionsTable";
import { AdminLlmModelAccessDialog } from "@/components/admin/llm/AdminLlmModelAccessDialog";
import { AdminLlmModelFormDialog } from "@/components/admin/llm/AdminLlmModelFormDialog";
import { AdminLlmModelsSection } from "@/components/admin/llm/AdminLlmModelsSection";
import { InstanceLlmSettingsCard } from "@/components/admin/llm/InstanceLlmSettingsCard";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import {
	type AdminLlmModelSaveBody,
	AdminLlmModelSaveError,
	saveAdminLlmModelSafely,
} from "@/lib/adminLlmModelSave";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/llm")({
	component: AdminLlmPage,
});

function AdminLlmPage() {
	const queryClient = useQueryClient();

	const [selectedConnectionId, setSelectedConnectionId] = useState<number | null>(null);
	const [connectionDialogOpen, setConnectionDialogOpen] = useState(false);
	const [editingConnection, setEditingConnection] = useState<LlmConnection | null>(null);
	const [mutatingConnectionId, setMutatingConnectionId] = useState<number | null>(null);
	const [probedModels, setProbedModels] = useState<{
		connectionId: number | null;
		models: string[];
	} | null>(null);

	const [modelDialogOpen, setModelDialogOpen] = useState(false);
	const [editingModel, setEditingModel] = useState<LlmModel | null>(null);
	const [accessModel, setAccessModel] = useState<LlmModel | null>(null);
	const [mutatingModelId, setMutatingModelId] = useState<number | null>(null);

	const connectionsQuery = useQuery(adminListLlmConnectionsOptions());
	const connections = connectionsQuery.data ?? [];
	const selectedConnection =
		connections.find((c) => c.id === selectedConnectionId) ?? connections[0];

	const modelsQuery = useQuery(adminListLlmModelsOptions());
	const allModels = modelsQuery.data ?? [];
	const modelCounts = allModels.reduce<Record<number, number>>((acc, model) => {
		acc[model.connectionId] = (acc[model.connectionId] ?? 0) + 1;
		return acc;
	}, {});
	const modelsForSelectedConnection = selectedConnection
		? allModels.filter((m) => m.connectionId === selectedConnection.id)
		: [];

	const workspacesQuery = useQuery(adminListWorkspacesOptions());
	const workspaceOptions = (workspacesQuery.data ?? []).map((w) => ({
		id: w.id,
		displayName: w.displayName,
		workspaceSlug: w.workspaceSlug,
	}));

	const settingsQuery = useQuery(adminGetLlmSettingsOptions());

	const invalidateConnections = () =>
		queryClient.invalidateQueries({ queryKey: adminListLlmConnectionsQueryKey() });
	const invalidateModels = () =>
		queryClient.invalidateQueries({ queryKey: adminListLlmModelsQueryKey() });

	const createConnection = useMutation({
		...adminCreateLlmConnectionMutation(),
		onSuccess: (created) => {
			invalidateConnections();
			setConnectionDialogOpen(false);
			setSelectedConnectionId(created.id);
			setProbedModels((current) =>
				current?.connectionId === null
					? { connectionId: created.id, models: current.models }
					: null,
			);
			toast.success("Connection added");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not add the connection")),
	});

	const updateConnection = useMutation({
		...adminUpdateLlmConnectionMutation(),
		onSuccess: () => {
			invalidateConnections();
			setConnectionDialogOpen(false);
			toast.success("Connection updated");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not update the connection")),
		onSettled: () => setMutatingConnectionId(null),
	});

	const deleteConnection = useMutation({
		...adminDeleteLlmConnectionMutation(),
		onSuccess: (_data, variables) => {
			invalidateConnections();
			if (variables.path.id === selectedConnectionId) setSelectedConnectionId(null);
			toast.success("Connection deleted");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not delete the connection")),
		onSettled: () => setMutatingConnectionId(null),
	});

	const probeDraft = useMutation({ ...adminProbeLlmConnectionDraftMutation() });
	const probeSaved = useMutation({ ...adminProbeLlmConnectionMutation() });

	const createModel = useMutation({ ...adminCreateLlmModelMutation() });
	const updateModel = useMutation({ ...adminUpdateLlmModelMutation() });
	const updatePrice = useMutation({ ...adminUpdateLlmModelPriceMutation() });
	const updateSharing = useMutation({ ...adminUpdateLlmModelSharingMutation() });
	const deleteModel = useMutation({
		...adminDeleteLlmModelMutation(),
		onSuccess: () => {
			invalidateModels();
			toast.success("Model deleted");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not delete the model")),
		onSettled: () => setMutatingModelId(null),
	});

	const updateSettings = useMutation({
		...adminUpdateLlmSettingsMutation(),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: adminGetLlmSettingsQueryKey() });
			toast.success("Settings saved");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not save settings")),
	});

	const isModelSaving =
		createModel.isPending ||
		updateModel.isPending ||
		updatePrice.isPending ||
		updateSharing.isPending;

	const handleSaveModel = async (body: AdminLlmModelSaveBody) => {
		if (!selectedConnection) return;
		try {
			await saveAdminLlmModelSafely({
				connectionId: selectedConnection.id,
				editing: editingModel,
				body,
				operations: {
					create: (connectionId, metadata) =>
						createModel.mutateAsync({ path: { connectionId }, body: metadata }),
					updateMetadata: (id, metadata) =>
						updateModel.mutateAsync({ path: { id }, body: metadata }),
					updatePrice: (id, price) => updatePrice.mutateAsync({ path: { id }, body: price }),
					updateSharing: (id, sharing) =>
						updateSharing.mutateAsync({ path: { id }, body: sharing }),
				},
			});
			invalidateModels();
			setModelDialogOpen(false);
			toast.success(editingModel ? "Model updated" : "Model added");
		} catch (error) {
			invalidateModels();
			if (error instanceof AdminLlmModelSaveError && error.modelId != null) {
				toast.error("Model saved inactive, but setup is incomplete", {
					description: "Review the model and save again before activating it.",
				});
			} else {
				toast.error(problemDetailOf(error, "Could not save the model"));
			}
		}
	};

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="flex flex-wrap items-start justify-between gap-3">
				<div className="space-y-1">
					<div className="flex items-center gap-2">
						<BrainCircuit className="size-6 text-muted-foreground" aria-hidden />
						<h1 className="text-2xl font-semibold">AI models</h1>
					</div>
					<p className="max-w-2xl text-sm text-muted-foreground">
						Connect OpenAI-compatible endpoints and share models with workspaces. Workspace admins
						never see the endpoint or key behind a shared model — only its name and price.
					</p>
				</div>
				<Button
					disabled={!connectionsQuery.isSuccess}
					onClick={() => {
						setEditingConnection(null);
						setProbedModels({ connectionId: null, models: [] });
						setConnectionDialogOpen(true);
					}}
				>
					<Plus className="size-4" aria-hidden />
					Add connection
				</Button>
			</header>

			<AdminLlmConnectionsTable
				connections={connections}
				modelCounts={modelCounts}
				modelCountsAvailable={modelsQuery.isSuccess}
				isLoading={connectionsQuery.isLoading}
				isError={connectionsQuery.isError}
				error={connectionsQuery.error}
				onRetry={() => connectionsQuery.refetch()}
				mutatingId={mutatingConnectionId}
				selectedId={selectedConnection?.id ?? null}
				onSelect={(connection) => {
					setSelectedConnectionId(connection.id);
				}}
				onEdit={(connection) => {
					setEditingConnection(connection);
					setProbedModels({ connectionId: connection.id, models: [] });
					setConnectionDialogOpen(true);
				}}
				onToggleEnabled={(connection, enabled) => {
					setMutatingConnectionId(connection.id);
					updateConnection.mutate({ path: { id: connection.id }, body: { enabled } });
				}}
				onDelete={(connection) => {
					setMutatingConnectionId(connection.id);
					deleteConnection.mutate({ path: { id: connection.id } });
				}}
				onAdd={() => {
					setEditingConnection(null);
					setProbedModels({ connectionId: null, models: [] });
					setConnectionDialogOpen(true);
				}}
			/>

			{selectedConnection &&
				(modelsQuery.isError ? (
					<Alert variant="destructive">
						<AlertCircle aria-hidden />
						<AlertTitle>Could not load models</AlertTitle>
						<AlertDescription>
							The catalog could not be loaded. Retry before changing this connection.
							<Button
								variant="outline"
								size="sm"
								className="mt-2"
								onClick={() => modelsQuery.refetch()}
							>
								Retry
							</Button>
						</AlertDescription>
					</Alert>
				) : modelsQuery.isLoading ? (
					<div
						className="flex h-32 items-center justify-center"
						role="status"
						aria-label="Loading models"
					>
						<Spinner className="size-6" />
					</div>
				) : (
					<AdminLlmModelsSection
						connectionDisplayName={selectedConnection.displayName}
						connectionEnabled={selectedConnection.enabled}
						workspaceOptions={workspaceOptions}
						models={modelsForSelectedConnection}
						mutatingId={mutatingModelId}
						onAdd={() => {
							setEditingModel(null);
							setModelDialogOpen(true);
						}}
						onEdit={(model) => {
							setEditingModel(model);
							setModelDialogOpen(true);
						}}
						onManageAccess={setAccessModel}
						onDelete={(model) => {
							setMutatingModelId(model.id);
							deleteModel.mutate({ path: { id: model.id } });
						}}
					/>
				))}

			{settingsQuery.isError ? (
				<Alert variant="destructive">
					<AlertCircle aria-hidden />
					<AlertTitle>Could not load AI policy</AlertTitle>
					<AlertDescription>
						Retry before changing instance-wide AI settings.
						<Button
							variant="outline"
							size="sm"
							className="mt-2"
							onClick={() => settingsQuery.refetch()}
						>
							Retry
						</Button>
					</AlertDescription>
				</Alert>
			) : (
				<InstanceLlmSettingsCard
					settings={settingsQuery.data}
					isLoading={settingsQuery.isLoading}
					isSubmitting={updateSettings.isPending}
					onSave={(body: UpdateInstanceLlmSettingsRequest) => updateSettings.mutate({ body })}
				/>
			)}

			<AdminLlmConnectionFormDialog
				open={connectionDialogOpen}
				onOpenChange={(open) => {
					setConnectionDialogOpen(open);
					if (!open) setProbedModels(null);
				}}
				editing={editingConnection}
				isSubmitting={createConnection.isPending || updateConnection.isPending}
				onCreate={(body: CreateLlmConnectionRequest) => createConnection.mutate({ body })}
				onUpdate={(id, body: UpdateLlmConnectionRequest) => {
					setMutatingConnectionId(id);
					updateConnection.mutate({ path: { id }, body });
				}}
				isProbing={probeDraft.isPending || probeSaved.isPending}
				onProbe={(request, callbacks) => {
					probeDraft.mutate(
						{ body: request },
						{
							onSuccess: callbacks.onSuccess,
							onError: (error) =>
								callbacks.onError(problemDetailOf(error, "the provider didn't answer.")),
						},
					);
				}}
				onProbeSaved={(id, callbacks) => {
					probeSaved.mutate(
						{ path: { id } },
						{
							onSuccess: callbacks.onSuccess,
							onError: (error) =>
								callbacks.onError(problemDetailOf(error, "the provider didn't answer.")),
						},
					);
				}}
				onProbed={(models) =>
					setProbedModels({ connectionId: editingConnection?.id ?? null, models })
				}
			/>

			<AdminLlmModelFormDialog
				open={modelDialogOpen}
				onOpenChange={setModelDialogOpen}
				editing={editingModel}
				workspaceOptions={workspaceOptions}
				probedModelIds={
					probedModels?.connectionId === selectedConnection?.id ? probedModels.models : []
				}
				isSubmitting={isModelSaving}
				onSave={handleSaveModel}
			/>

			<AdminLlmModelAccessDialog
				open={accessModel != null}
				onOpenChange={(open) => {
					if (!open && !updateSharing.isPending) setAccessModel(null);
				}}
				model={accessModel}
				workspaceOptions={workspaceOptions}
				isLoadingWorkspaces={workspacesQuery.isLoading}
				isWorkspaceError={workspacesQuery.isError}
				onRetryWorkspaces={() => workspacesQuery.refetch()}
				isSubmitting={updateSharing.isPending}
				onSave={(body) => {
					if (!accessModel) return;
					setMutatingModelId(accessModel.id);
					updateSharing.mutate(
						{ path: { id: accessModel.id }, body },
						{
							onSuccess: () => {
								invalidateModels();
								setAccessModel(null);
								toast.success("Workspace access updated");
							},
							onError: (error) =>
								toast.error(problemDetailOf(error, "Could not update workspace access")),
							onSettled: () => setMutatingModelId(null),
						},
					);
				}}
			/>
		</div>
	);
}
