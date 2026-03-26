import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useState } from "react";
import { toast } from "sonner";
import {
	createPracticeMutation,
	deletePracticeMutation,
	listPracticesOptions,
	listPracticesQueryKey,
	setActiveMutation,
	updatePracticeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { CreatePracticeRequest, UpdatePracticeRequest } from "@/api/types.gen";
import { AdminPracticesPage } from "@/components/admin/AdminPracticesPage";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/_admin/practices")({
	component: AdminPracticesContainer,
});

function AdminPracticesContainer() {
	const queryClient = useQueryClient();
	const {
		workspaceSlug,
		isLoading: isWorkspaceLoading,
		error: workspaceError,
	} = useActiveWorkspaceSlug();

	const [togglingPractices, setTogglingPractices] = useState<Set<string>>(new Set());

	// Practices list query
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

	// Create practice mutation
	const createPractice = useMutation({
		...createPracticeMutation(),
		onSuccess: () => {
			invalidatePractices();
			toast.success("Practice created successfully");
		},
		onError: (error) => {
			// 409 = slug already exists
			const status = (error as { status?: number }).status;
			if (status === 409) {
				toast.error("A practice with this slug already exists in this workspace");
			} else {
				toast.error("Failed to create practice");
			}
		},
	});

	// Update practice mutation
	const updatePractice = useMutation({
		...updatePracticeMutation(),
		onSuccess: () => {
			invalidatePractices();
			toast.success("Practice updated successfully");
		},
		onError: () => {
			toast.error("Failed to update practice");
		},
	});

	// Delete practice mutation
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

	// Set active mutation
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

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	const isLoading = isWorkspaceLoading || isPracticesLoading || !workspaceSlug;

	if (workspaceError || practicesError) {
		const errorMessage = (workspaceError as Error)?.message || (practicesError as Error)?.message;
		toast.error(`Failed to load data: ${errorMessage}`);
	}

	const handleCreatePractice = async (data: CreatePracticeRequest) => {
		if (!workspaceSlug) return;
		await createPractice.mutateAsync({
			path: { workspaceSlug },
			body: data,
		});
	};

	const handleUpdatePractice = async (slug: string, data: UpdatePracticeRequest) => {
		if (!workspaceSlug) return;
		await updatePractice.mutateAsync({
			path: { workspaceSlug, practiceSlug: slug },
			body: data,
		});
	};

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
			practices={practices ?? []}
			isLoading={isLoading}
			isCreating={createPractice.isPending}
			isUpdating={updatePractice.isPending}
			isDeleting={deletePractice.isPending}
			togglingPractices={togglingPractices}
			onCreatePractice={handleCreatePractice}
			onUpdatePractice={handleUpdatePractice}
			onDeletePractice={handleDeletePractice}
			onSetActive={handleSetActive}
		/>
	);
}
