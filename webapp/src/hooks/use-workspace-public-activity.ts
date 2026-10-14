import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import {
	getPublicActivityHiddenCountOptions,
	getPublicActivityHiddenCountQueryKey,
	getWorkspacePublicActivitySettingsOptions,
	getWorkspacePublicActivitySettingsQueryKey,
	listHiddenContributorsOptions,
	listHiddenContributorsQueryKey,
	listWorkspacesQueryKey,
	updatePublicActivityMutation,
	updatePublicActivityObjectionMutation,
} from "@/api/@tanstack/react-query.gen";
import type { UpdateWorkspacePublicActivityRequest } from "@/api/types.gen";
import type { WorkspacePublicActivityState } from "@/components/admin/settings/WorkspacePublicActivitySettings";
import { panelState } from "@/components/common/panel-state";
import { problemDetailOf } from "@/lib/problem-detail";

/**
 * A workspace's public activity page as its admin settings show it: whether it is on, whether search
 * engines may list it, how many people it leaves out, and the two changes. `live` is the
 * server-computed publication status from the workspace list.
 */
export function useWorkspacePublicActivity({
	workspaceSlug,
	live,
}: {
	workspaceSlug: string | undefined;
	live: boolean;
}) {
	const queryClient = useQueryClient();
	const path = { workspaceSlug: workspaceSlug ?? "" };
	const settings = useQuery({
		...getWorkspacePublicActivitySettingsOptions({ path }),
		enabled: workspaceSlug !== undefined,
	});
	const hidden = useQuery({
		...getPublicActivityHiddenCountOptions({ path }),
		enabled: settings.data?.enabled === true,
	});
	const hiddenContributors = useQuery({
		...listHiddenContributorsOptions({ path }),
		enabled: workspaceSlug !== undefined,
	});
	// Each write names, and refreshes, the workspace it was submitted for: the reply can arrive after
	// the admin has moved to another workspace.
	const showAgain = useMutation({
		...updatePublicActivityObjectionMutation(),
		onSuccess: (_data, { path: { workspaceSlug: submitted } }) => {
			const workspace = { path: { workspaceSlug: submitted } };
			void queryClient.invalidateQueries({ queryKey: listHiddenContributorsQueryKey(workspace) });
			void queryClient.invalidateQueries({
				queryKey: getPublicActivityHiddenCountQueryKey(workspace),
			});
			toast.success("Shown in activity again", { description: inWorkspace(submitted) });
		},
		onError: (error, { path: { workspaceSlug: submitted } }) =>
			toast.error(problemDetailOf(error, "We could not show this person again. Try again."), {
				description: inWorkspace(submitted),
			}),
	});
	const update = useMutation({
		...updatePublicActivityMutation(),
		onSuccess: (data, { path: submitted, body }) => {
			queryClient.setQueryData(
				getWorkspacePublicActivitySettingsQueryKey({ path: submitted }),
				data,
			);
			// The list carries the publication status, and the count follows what the page lists.
			void queryClient.invalidateQueries({ queryKey: listWorkspacesQueryKey() });
			void queryClient.invalidateQueries({
				queryKey: getPublicActivityHiddenCountQueryKey({ path: submitted }),
			});
			announce(body, submitted.workspaceSlug);
		},
		onError: (error, { path: submitted }) => {
			void queryClient.invalidateQueries({
				queryKey: getWorkspacePublicActivitySettingsQueryKey({ path: submitted }),
			});
			toast.error(
				problemDetailOf(error, "We could not change the public activity page. Try again."),
				{ description: inWorkspace(submitted.workspaceSlug) },
			);
		},
	});

	const change = (patch: Partial<UpdateWorkspacePublicActivityRequest>) => {
		if (settings.data === undefined) {
			return;
		}
		const body = {
			publicActivityEnabled: settings.data.enabled,
			allowSearchEngines: settings.data.allowSearchEngines,
			...patch,
		};
		// A page that is off has no search engine choice: the server forgets it, so this says the same.
		update.mutate({
			path,
			body: { ...body, allowSearchEngines: body.publicActivityEnabled && body.allowSearchEngines },
		});
	};

	// A failed background read keeps the last available restoration controls.
	const hiddenContributorsState =
		hiddenContributors.data === undefined
			? panelState(hiddenContributors, (people) => ({ status: "ready" as const, people }))
			: { status: "ready" as const, people: hiddenContributors.data };

	let state: WorkspacePublicActivityState = { status: "loading" };
	if (settings.data !== undefined) {
		const pendingBody = update.isPending ? update.variables.body : undefined;
		state = {
			status: "ready",
			enabled: settings.data.enabled,
			allowSearchEngines: settings.data.allowSearchEngines,
			live,
			hiddenPeople: hidden.data?.hiddenPeople,
			hiddenContributors: hiddenContributorsState,
			restoring: showAgain.isPending ? showAgain.variables.path.userId : undefined,
			pending: pendingBody && pendingChange(pendingBody, settings.data.enabled),
		};
	} else if (settings.isError) {
		state = {
			status: "error",
			error: settings.error,
			onRetry: () => {
				void settings.refetch();
			},
		};
	}
	return {
		state,
		onEnabledChange: (publicActivityEnabled: boolean) => change({ publicActivityEnabled }),
		onSearchEnginesChange: (allowSearchEngines: boolean) => change({ allowSearchEngines }),
		onShowAgain: (userId: number) =>
			showAgain.mutate({ path: { ...path, userId }, query: { hidden: false } }),
	};
}

/** The page's switch if the request turns it on or off, else the search engines' one. */
function pendingChange(
	{ publicActivityEnabled }: UpdateWorkspacePublicActivityRequest,
	enabled: boolean,
): "enabled" | "search-engines" {
	return publicActivityEnabled === enabled ? "search-engines" : "enabled";
}

/** Which workspace a notification is about, since the admin may be looking at another one by then. */
function inWorkspace(workspaceSlug: string): string {
	return `In workspace ${workspaceSlug}.`;
}

/** Says what was saved: whether the page is public is the workspace list's to tell, after this. */
function announce(
	{ publicActivityEnabled }: UpdateWorkspacePublicActivityRequest,
	workspaceSlug: string,
) {
	if (publicActivityEnabled) {
		toast.success("Public activity settings saved", { description: inWorkspace(workspaceSlug) });
		return;
	}
	toast.success("The public activity page is off", {
		description: `${inWorkspace(workspaceSlug)} Visitors see the change when they load the page again, at most about a minute later.`,
	});
}
