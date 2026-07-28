import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { KeyRound, Plus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
	adminCreateLoginProviderMutation,
	adminDeleteLoginProviderMutation,
	adminListLoginProvidersOptions,
	adminListLoginProvidersQueryKey,
	adminUpdateLoginProviderMutation,
} from "@/api/@tanstack/react-query.gen";
import type {
	CreateLoginProviderRequest,
	LoginProviderView,
	UpdateLoginProviderRequest,
} from "@/api/types.gen";
import { LoginProviderFormDialog } from "@/components/admin/login-providers/LoginProviderFormDialog";
import { LoginProvidersTable } from "@/components/admin/login-providers/LoginProvidersTable";
import { Button } from "@/components/ui/button";
import { filedUnder, usePendingMutationIds } from "@/hooks/use-pending-mutation-ids";
import { instanceAdminHead } from "@/lib/page-title";
import { problemDetailOf } from "@/lib/problem-detail";

export const Route = createFileRoute("/_authenticated/admin/login-providers")({
	head: instanceAdminHead("Login providers"),
	component: AdminLoginProvidersPage,
});

/** One prefix for toggle and delete, so one lookup covers both. Creation has no row to disable. */
const PROVIDER_WRITE_MUTATION_KEY = ["adminWriteLoginProvider"];

function AdminLoginProvidersPage() {
	const queryClient = useQueryClient();
	const listQuery = useQuery(adminListLoginProvidersOptions());
	const providers: LoginProviderView[] = listQuery.data ?? [];

	const [dialogOpen, setDialogOpen] = useState(false);
	const [editing, setEditing] = useState<LoginProviderView | null>(null);

	const invalidate = () =>
		queryClient.invalidateQueries({ queryKey: adminListLoginProvidersQueryKey() });

	const createMutation = useMutation({
		...adminCreateLoginProviderMutation(),
		onSuccess: () => {
			invalidate();
			setDialogOpen(false);
			toast.success("Login provider added");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not add the login provider")),
	});

	const updateMutation = useMutation({
		...filedUnder(PROVIDER_WRITE_MUTATION_KEY, adminUpdateLoginProviderMutation()),
		onSuccess: () => {
			invalidate();
			setDialogOpen(false);
			toast.success("Login provider updated");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not update the login provider")),
	});

	const deleteMutation = useMutation({
		...filedUnder(PROVIDER_WRITE_MUTATION_KEY, adminDeleteLoginProviderMutation()),
		onSuccess: () => {
			invalidate();
			toast.success("Login provider deleted");
		},
		onError: (error) => toast.error(problemDetailOf(error, "Could not delete the login provider")),
	});

	const mutatingIds = usePendingMutationIds<{ path: { registrationId: string } }, string>(
		PROVIDER_WRITE_MUTATION_KEY,
		(variables) => variables.path.registrationId,
	);

	const openCreate = () => {
		setEditing(null);
		setDialogOpen(true);
	};
	const openEdit = (provider: LoginProviderView) => {
		setEditing(provider);
		setDialogOpen(true);
	};

	const handleCreate = (body: CreateLoginProviderRequest) => createMutation.mutate({ body });
	const handleUpdate = (registrationId: string, body: UpdateLoginProviderRequest) => {
		updateMutation.mutate({ path: { registrationId }, body });
	};
	const handleToggleEnabled = (provider: LoginProviderView, enabled: boolean) => {
		updateMutation.mutate({ path: { registrationId: provider.registrationId }, body: { enabled } });
	};
	const handleDelete = (provider: LoginProviderView) => {
		deleteMutation.mutate({ path: { registrationId: provider.registrationId } });
	};

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="flex flex-wrap items-start justify-between gap-3">
				<div className="space-y-1">
					<div className="flex items-center gap-2">
						<KeyRound className="size-6 text-muted-foreground" aria-hidden />
						<h1 className="text-2xl font-semibold">Login providers</h1>
					</div>
					<p className="max-w-2xl text-sm text-muted-foreground">
						Configure OAuth providers for sign-in and account linking. Slack and Outline are
						link-only — users connect them from Settings, they are never offered on the sign-in
						page. Slack's redirect URI here is separate from the Slack app-install callback.
					</p>
				</div>
				<Button onClick={openCreate}>
					<Plus className="size-4" aria-hidden />
					Add provider
				</Button>
			</header>

			<LoginProvidersTable
				providers={providers}
				isLoading={listQuery.isLoading}
				isError={listQuery.isError}
				error={listQuery.error}
				onRetry={() => listQuery.refetch()}
				mutatingIds={mutatingIds}
				onEdit={openEdit}
				onToggleEnabled={handleToggleEnabled}
				onDelete={handleDelete}
				onAdd={openCreate}
			/>

			<LoginProviderFormDialog
				open={dialogOpen}
				onOpenChange={setDialogOpen}
				editing={editing}
				isSubmitting={createMutation.isPending || updateMutation.isPending}
				onCreate={handleCreate}
				onUpdate={handleUpdate}
			/>
		</div>
	);
}
