import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { toast } from "sonner";
import {
	configureAgentMutation,
	deleteAgentMutation,
	getLlmUsageReportOptions,
	getWorkspaceOptions,
	listAgentsOptions,
	listAgentsQueryKey,
	workspaceGetLlmSettingsOptions,
	workspaceListAvailableLlmModelsOptions,
} from "@/api/@tanstack/react-query.gen";
import type { AgentBinding } from "@/api/types.gen";
import { AgentBindingsPage, PURPOSE_TITLES } from "@/components/admin/ai/AgentBindingsPage";
import { currentMonthUtc } from "@/components/admin/usage/usage-utils";
import { filedUnder, usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";
import { workspaceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/models")({
	head: workspaceAdminHead("AI models"),
	component: ModelsContainer,
});

type Purpose = AgentBinding["purpose"];

/** Save and turn-off share one prefix, so one {@link usePendingMutationIds} lookup covers both. */
const AGENT_WRITE_MUTATION_KEY = ["workspaceWriteAgent"];

function ModelsContainer() {
	const { workspaceSlug } = Route.useParams();
	const queryClient = useQueryClient();
	// Scoped to the workspace: switching workspaces mid-save otherwise disables the new workspace's
	// card on the strength of the old one's request.
	const agentWriteKey = [...AGENT_WRITE_MUTATION_KEY, workspaceSlug];

	const bindingsQuery = useQuery(listAgentsOptions({ path: { workspaceSlug } }));
	const workspaceQuery = useQuery(getWorkspaceOptions({ path: { workspaceSlug } }));
	const llmSettingsQuery = useQuery(workspaceGetLlmSettingsOptions({ path: { workspaceSlug } }));
	const availableModelsQuery = useQuery(
		workspaceListAvailableLlmModelsOptions({ path: { workspaceSlug } }),
	);
	const usageQuery = useQuery({
		...getLlmUsageReportOptions({ path: { workspaceSlug }, query: { month: currentMonthUtc() } }),
		staleTime: 60_000,
	});

	// In precedence order: the first to fail supplies the ProblemDetail and the status the alert
	// classifies on, and the bindings are the one that answers the page's own question. Usage is not
	// among them — the page is readable without it.
	const pageQueries = [bindingsQuery, workspaceQuery, llmSettingsQuery, availableModelsQuery];

	const bindingsKey = listAgentsQueryKey({ path: { workspaceSlug } });
	const invalidateBindings = () => queryClient.invalidateQueries({ queryKey: bindingsKey });

	/**
	 * Synchronously, because the reseed key changes on the very next render and whatever the cache
	 * holds at that moment is what the admin reads back. `invalidateQueries` only schedules a refetch,
	 * so on its own it would remount the card against the pre-save array. The invalidation still
	 * follows, to supersede a refetch that was already in flight when the write landed.
	 */
	const cacheSavedBinding = (saved: AgentBinding) =>
		queryClient.setQueryData<AgentBinding[]>(bindingsKey, (current) => {
			const bindings = current ?? [];
			return bindings.some((b) => b.purpose === saved.purpose)
				? bindings.map((b) => (b.purpose === saved.purpose ? saved : b))
				: [...bindings, saved];
		});

	const dropCachedBinding = (purpose: Purpose) =>
		queryClient.setQueryData<AgentBinding[]>(bindingsKey, (current) =>
			(current ?? []).filter((b) => b.purpose !== purpose),
		);

	// Bumped only by this admin's own completed write, and only for the purpose they wrote: anything
	// else would remount a card over an edit in progress.
	const [saveRevisions, setSaveRevisions] = useState<Partial<Record<Purpose, number>>>({});
	const bumpSaveRevision = (purpose: Purpose) =>
		setSaveRevisions((current) => ({ ...current, [purpose]: (current[purpose] ?? 0) + 1 }));

	const configureAgent = useMutation({
		...filedUnder(agentWriteKey, configureAgentMutation()),
		onSuccess: (saved, variables) => {
			cacheSavedBinding(saved);
			bumpSaveRevision(variables.path.purpose);
			invalidateBindings();
			toast.success(`${PURPOSE_TITLES[variables.path.purpose]} saved`);
		},
		onError: (error, variables) => {
			toast.error(`Couldn't save ${PURPOSE_TITLES[variables.path.purpose].toLowerCase()}`, {
				description: problemDetailOf(error),
			});
		},
	});

	const deleteAgent = useMutation({
		...filedUnder(agentWriteKey, deleteAgentMutation()),
		onSuccess: (_data, variables) => {
			dropCachedBinding(variables.path.purpose);
			bumpSaveRevision(variables.path.purpose);
			invalidateBindings();
			toast.success(`${PURPOSE_TITLES[variables.path.purpose]} turned off`);
		},
		onError: (error, variables) => {
			toast.error(`Couldn't turn off ${PURPOSE_TITLES[variables.path.purpose].toLowerCase()}`, {
				description: problemDetailOf(error),
			});
		},
	});

	const pendingPurposes = usePendingMutationIds<{ path: { purpose: Purpose } }, Purpose>(
		agentWriteKey,
		(variables) => variables.path.purpose,
	);

	return (
		<AgentBindingsPage
			workspaceSlug={workspaceSlug}
			bindings={bindingsQuery.data ?? []}
			availableModels={availableModelsQuery.data ?? []}
			practicesEnabled={workspaceQuery.data?.practicesEnabled ?? false}
			mentorEnabled={workspaceQuery.data?.mentorEnabled ?? false}
			ownProviderAllowed={llmSettingsQuery.data?.ownProviderAllowed ?? false}
			usage={usageQuery.data}
			isLoading={pageQueries.some((query) => query.isLoading)}
			isError={pageQueries.some((query) => query.isError)}
			loadError={pageQueries.find((query) => query.error != null)?.error}
			pendingPurposes={pendingPurposes}
			saveRevisions={saveRevisions}
			onRetry={() => {
				for (const query of pageQueries) {
					query.refetch();
				}
			}}
			onSave={(purpose, body) => configureAgent.mutate({ path: { workspaceSlug, purpose }, body })}
			onTurnOff={(purpose) => deleteAgent.mutate({ path: { workspaceSlug, purpose } })}
		/>
	);
}
