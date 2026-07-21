import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { BrainCircuit, Plus } from "lucide-react";
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
	adminUpdateLlmConnectionMutation,
	adminUpdateLlmModelMutation,
	adminUpdateLlmModelPriceMutation,
	adminUpdateLlmModelSharingMutation,
	adminUpdateLlmSettingsMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	CreateLlmConnectionRequest,
	CreateLlmModelRequest,
	LlmConnection,
	LlmModel,
	UpdateInstanceLlmSettingsRequest,
	UpdateLlmConnectionRequest,
	UpdateLlmModelRequest,
} from "@/api/types.gen";
import { AdminLlmConnectionFormDialog } from "@/components/admin/llm/AdminLlmConnectionFormDialog";
import { AdminLlmConnectionsTable } from "@/components/admin/llm/AdminLlmConnectionsTable";
import {
	AdminLlmModelFormDialog,
	type AdminLlmModelSaveBody,
} from "@/components/admin/llm/AdminLlmModelFormDialog";
import { AdminLlmModelsSection } from "@/components/admin/llm/AdminLlmModelsSection";
import { InstanceLlmSettingsCard } from "@/components/admin/llm/InstanceLlmSettingsCard";
import { Button } from "@/components/ui/button";
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
	const [lastProbedModels, setLastProbedModels] = useState<string[]>([]);

	const [modelDialogOpen, setModelDialogOpen] = useState(false);
	const [editingModel, setEditingModel] = useState<LlmModel | null>(null);
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
			if (editingModel) {
				await Promise.all([
					updateModel.mutateAsync({
						path: { id: editingModel.id },
						body: body.metadata as UpdateLlmModelRequest,
					}),
					updatePrice.mutateAsync({ path: { id: editingModel.id }, body: body.price }),
					updateSharing.mutateAsync({ path: { id: editingModel.id }, body: body.sharing }),
				]);
			} else {
				const created = await createModel.mutateAsync({
					path: { connectionId: selectedConnection.id },
					body: body.metadata as CreateLlmModelRequest,
				});
				await Promise.all([
					updatePrice.mutateAsync({ path: { id: created.id }, body: body.price }),
					updateSharing.mutateAsync({ path: { id: created.id }, body: body.sharing }),
				]);
			}
			invalidateModels();
			setModelDialogOpen(false);
			toast.success(editingModel ? "Model updated" : "Model added");
		} catch (error) {
			toast.error(problemDetailOf(error, "Could not save the model"));
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
						Connect LLM providers and share models with workspaces. Workspace admins never see the
						endpoint or key behind a shared model — only its name and price.
					</p>
				</div>
				<Button
					onClick={() => {
						setEditingConnection(null);
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
				isLoading={connectionsQuery.isLoading}
				isError={connectionsQuery.isError}
				error={connectionsQuery.error}
				onRetry={() => connectionsQuery.refetch()}
				mutatingId={mutatingConnectionId}
				selectedId={selectedConnection?.id ?? null}
				onSelect={(connection) => setSelectedConnectionId(connection.id)}
				onEdit={(connection) => {
					setEditingConnection(connection);
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
					setConnectionDialogOpen(true);
				}}
			/>

			{selectedConnection && (
				<AdminLlmModelsSection
					connectionDisplayName={selectedConnection.displayName}
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
					onDelete={(model) => {
						setMutatingModelId(model.id);
						deleteModel.mutate({ path: { id: model.id } });
					}}
				/>
			)}

			<InstanceLlmSettingsCard
				settings={settingsQuery.data}
				isLoading={settingsQuery.isLoading}
				isSubmitting={updateSettings.isPending}
				onSave={(body: UpdateInstanceLlmSettingsRequest) => updateSettings.mutate({ body })}
			/>

			<AdminLlmConnectionFormDialog
				open={connectionDialogOpen}
				onOpenChange={setConnectionDialogOpen}
				editing={editingConnection}
				isSubmitting={createConnection.isPending || updateConnection.isPending}
				onCreate={(body: CreateLlmConnectionRequest) => createConnection.mutate({ body })}
				onUpdate={(id, body: UpdateLlmConnectionRequest) => {
					setMutatingConnectionId(id);
					updateConnection.mutate({ path: { id }, body });
				}}
				isProbing={probeDraft.isPending}
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
				onProbed={setLastProbedModels}
			/>

			<AdminLlmModelFormDialog
				open={modelDialogOpen}
				onOpenChange={setModelDialogOpen}
				editing={editingModel}
				workspaceOptions={workspaceOptions}
				probedModelIds={lastProbedModels}
				isSubmitting={isModelSaving}
				onSave={handleSaveModel}
			/>
		</div>
	);
}
