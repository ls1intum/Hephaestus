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

/**
 * Save and turn-off share one prefix, so one lookup answers "is this card busy" for both.
 *
 * One `useMutation` observer per operation tracks only its most recent call, so a single
 * `variables?.path.purpose` cannot describe two cards: save practice detection, then save the mentor
 * without waiting, and the observer's variables flip to `MENTOR` — practice detection re-enables
 * with its own PUT still in flight, and a second click sends it twice. The cache holds one entry per
 * call, so ask it instead.
 */
const AGENT_WRITE_MUTATION_KEY = ["workspaceWriteAgent"];

function ModelsContainer() {
	// The slug is validated by the admin layout's beforeLoad, so it is always present here.
	const { workspaceSlug } = Route.useParams();
	const queryClient = useQueryClient();
	// Scoped to the workspace, like every other write key on these surfaces: switching workspaces
	// mid-save otherwise disables the new workspace's card on the strength of the old one's request.
	const agentWriteKey = [...AGENT_WRITE_MUTATION_KEY, workspaceSlug];

	const bindingsQuery = useQuery(listAgentsOptions({ path: { workspaceSlug } }));
	// Whether a purpose may run at all is a property of the workspace, not of any AI settings blob —
	// it is the same flag the sidebar and the practices pages read.
	const workspaceQuery = useQuery(getWorkspaceOptions({ path: { workspaceSlug } }));
	// Whether this workspace may register providers of its own is set by the instance, so it is a
	// separate question from anything the workspace itself configures.
	const llmSettingsQuery = useQuery(workspaceGetLlmSettingsOptions({ path: { workspaceSlug } }));
	const availableModelsQuery = useQuery(
		workspaceListAvailableLlmModelsOptions({ path: { workspaceSlug } }),
	);
	const usageQuery = useQuery({
		...getLlmUsageReportOptions({ path: { workspaceSlug }, query: { month: currentMonthUtc() } }),
		staleTime: 60_000,
	});

	const bindingsKey = listAgentsQueryKey({ path: { workspaceSlug } });
	const invalidateBindings = () => queryClient.invalidateQueries({ queryKey: bindingsKey });

	/**
	 * Put this write's own result into the cache, synchronously, before the card is reseeded from it.
	 *
	 * Reseeding is keyed on `saveRevisions`, and a key change takes effect on the very next render —
	 * so whatever the cache holds at that moment is what the admin reads back. `invalidateQueries`
	 * does not touch `data`; it schedules a refetch. Bumping the revision on the strength of it alone
	 * remounts the card against the *pre-save* array: the picker snaps back to the model that was
	 * just replaced, under a success toast, and stays there, because the key never changes again when
	 * the refetch lands. Saving again from that screen writes the old model back.
	 *
	 * The response is the server's own view of the binding it just stored, so this is not an
	 * optimistic guess. The invalidation still follows, to reconcile anything computed elsewhere and
	 * to supersede a refetch that was already in flight when the write landed.
	 */
	const cacheSavedBinding = (saved: AgentBinding) =>
		queryClient.setQueryData<AgentBinding[]>(bindingsKey, (current) => {
			const bindings = current ?? [];
			return bindings.some((b) => b.purpose === saved.purpose)
				? bindings.map((b) => (b.purpose === saved.purpose ? saved : b))
				: [...bindings, saved];
		});

	/** The same, for a turn-off: the binding is gone, and the card reseeds to its defaults. */
	const dropCachedBinding = (purpose: Purpose) =>
		queryClient.setQueryData<AgentBinding[]>(bindingsKey, (current) =>
			(current ?? []).filter((b) => b.purpose !== purpose),
		);

	// Bumped only by this admin's own completed write, and only for the purpose they wrote — see
	// `saveRevisions` on `AgentBindingsPage` for why nothing else may reseed a card.
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

	// One mutation pair serves both cards, so "pending" has to name *which* purposes are in flight —
	// and there can be two at once, which no single observer's `variables` can say.
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
			isLoading={
				bindingsQuery.isLoading ||
				workspaceQuery.isLoading ||
				llmSettingsQuery.isLoading ||
				availableModelsQuery.isLoading
			}
			isError={
				bindingsQuery.isError ||
				workspaceQuery.isError ||
				llmSettingsQuery.isError ||
				availableModelsQuery.isError
			}
			// The first of the four in this order supplies the ProblemDetail and the status the alert
			// classifies on — a 403 must not offer a Retry that would be refused identically. Fixed
			// precedence, not whichever failed first: the bindings answer the page's own question, so
			// when several are down it is the one worth naming.
			loadError={
				bindingsQuery.error ??
				workspaceQuery.error ??
				llmSettingsQuery.error ??
				availableModelsQuery.error
			}
			pendingPurposes={pendingPurposes}
			saveRevisions={saveRevisions}
			onRetry={() => {
				bindingsQuery.refetch();
				workspaceQuery.refetch();
				llmSettingsQuery.refetch();
				availableModelsQuery.refetch();
			}}
			onSave={(purpose, body) => configureAgent.mutate({ path: { workspaceSlug, purpose }, body })}
			onTurnOff={(purpose) => deleteAgent.mutate({ path: { workspaceSlug, purpose } })}
		/>
	);
}
