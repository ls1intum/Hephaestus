import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
	deletePracticeMutation,
	listPracticesOptions,
	listPracticesQueryKey,
	setActiveMutation,
} from "@/api/@tanstack/react-query.gen";
import { AdminPracticesPage } from "@/components/admin/practices/AdminPracticesPage";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/practices/")({
	component: PracticesListContainer,
});

function PracticesListContainer() {
	const queryClient = useQueryClient();
	const { workspaceSlug } = Route.useParams();
	const { isLoading: isWorkspaceLoading, error: workspaceError } = useActiveWorkspaceSlug();

	const [togglingPractices, setTogglingPractices] = useState<Set<string>>(new Set());

	const practicesQueryOptions = listPracticesOptions({
		path: { workspaceSlug: workspaceSlug ?? "" },
	});
	const {
		data: practices,
		isLoading: isPracticesLoading,
		error: practicesError,
	} = useQuery({
		...practicesQueryOptions,
		enabled: Boolean(workspaceSlug) && (practicesQueryOptions.enabled ?? true),
	});

	const invalidatePractices = () => {
		queryClient.invalidateQueries({
			queryKey: listPracticesQueryKey({ path: { workspaceSlug: workspaceSlug ?? "" } }),
		});
	};

	const deletePractice = useMutation({
		...deletePracticeMutation(),
		onSuccess: () => {
			invalidatePractices();
			toast.success("Practice deleted successfully");
		},
		onError: () => {
			toast.error("Failed to delete practice");
		},
	});

	const setActive = useMutation({
		...setActiveMutation(),
		onSuccess: () => {
			invalidatePractices();
		},
		onError: (_error, variables) => {
			toast.error(`Failed to toggle practice "${variables.path.practiceSlug}"`);
		},
		onSettled: (_data, _error, variables) => {
			setTogglingPractices((prev) => {
				const next = new Set(prev);
				next.delete(variables.path.practiceSlug);
				return next;
			});
		},
	});

	useEffect(() => {
		if (workspaceError || practicesError) {
			const err = workspaceError ?? practicesError;
			const message = err instanceof Error ? err.message : "Unknown error";
			toast.error(`Failed to load data: ${message}`);
		}
	}, [workspaceError, practicesError]);

	const handleDeletePractice = async (slug: string) => {
		if (!workspaceSlug) return;
		await deletePractice.mutateAsync({
			path: { workspaceSlug, practiceSlug: slug },
		});
	};

	const handleSetActive = (slug: string, active: boolean) => {
		if (!workspaceSlug) return;
		setTogglingPractices((prev) => new Set(prev).add(slug));
		setActive.mutate({
			path: { workspaceSlug, practiceSlug: slug },
			body: { active },
		});
	};

	return (
		<AdminPracticesPage
			workspaceSlug={workspaceSlug ?? ""}
			practices={practices ?? []}
			isLoading={isWorkspaceLoading || isPracticesLoading || !workspaceSlug}
			isDeleting={deletePractice.isPending}
			togglingPractices={togglingPractices}
			onDeletePractice={handleDeletePractice}
			onSetActive={handleSetActive}
		/>
	);
}
