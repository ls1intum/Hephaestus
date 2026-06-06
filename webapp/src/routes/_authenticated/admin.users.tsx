import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { ShieldCheck, ShieldOff, UserCog, Users } from "lucide-react";
import { useDeferredValue, useEffect, useState } from "react";
import { toast } from "sonner";
import {
	adminListUsersInfiniteOptions,
	adminListUsersQueryKey,
	adminRevokeUserSessionsMutation,
	adminUpdateUserMutation,
	impersonateMutation,
} from "@/api/@tanstack/react-query.gen";
import type { AdminAccountView } from "@/api/types.gen";
import { AdminUsersTable } from "@/components/admin/users/AdminUsersTable";
import { ChangeRoleDialog } from "@/components/admin/users/ChangeRoleDialog";
import { ImpersonateDialog } from "@/components/admin/users/ImpersonateDialog";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/integrations/auth/AuthContext";
import { problemDetailOf } from "@/lib/problem-detail";

const PAGE_SIZE = 25;

export const Route = createFileRoute("/_authenticated/admin/users")({
	component: AdminUsersPage,
});

type DialogTarget = { user: AdminAccountView } | null;

function AdminUsersPage() {
	const queryClient = useQueryClient();
	const { getUserId } = useAuth();
	// `getUserId()` is undefined until the current user loads; only coerce a real id so the
	// self-impersonation guard never compares against NaN during the load window.
	const userId = getUserId();
	const currentUserId = userId != null ? Number(userId) : undefined;

	// Server has no search param (AdminListUsersData.query is { page, size } only), so we
	// filter the already-loaded rows client-side. useDeferredValue keeps the input responsive
	// by deprioritizing the heavy re-filter render (it does not debounce/throttle).
	const [search, setSearch] = useState("");
	const deferredSearch = useDeferredValue(search);

	const [roleTarget, setRoleTarget] = useState<DialogTarget>(null);
	const [impersonateTarget, setImpersonateTarget] = useState<DialogTarget>(null);
	const [signOutTarget, setSignOutTarget] = useState<DialogTarget>(null);

	// Offset pagination: the endpoint returns a bare array with no total/cursor, so we treat
	// a full page as "there may be more" and stop when a short page comes back.
	const listQuery = useInfiniteQuery({
		...adminListUsersInfiniteOptions({ query: { size: PAGE_SIZE } }),
		initialPageParam: 0,
		getNextPageParam: (lastPage: AdminAccountView[], allPages: AdminAccountView[][]) =>
			lastPage.length === PAGE_SIZE ? allPages.length : undefined,
	});

	const allUsers: AdminAccountView[] = listQuery.data?.pages.flat() ?? [];

	// Search filters loaded rows client-side (the API has no `q` param). While a term is active,
	// eagerly pull the remaining pages so the filter sees EVERY user — otherwise a match on an
	// un-fetched page shows a false "No users found".
	useEffect(() => {
		if (deferredSearch.trim() && listQuery.hasNextPage && !listQuery.isFetchingNextPage) {
			listQuery.fetchNextPage();
		}
	}, [
		deferredSearch,
		listQuery.hasNextPage,
		listQuery.isFetchingNextPage,
		listQuery.fetchNextPage,
	]);

	const term = deferredSearch.trim().toLowerCase();
	const filteredUsers = term
		? allUsers.filter((u) =>
				[u.displayName, u.primaryEmail, u.appRole, u.status, String(u.id ?? "")]
					.filter(Boolean)
					.some((field) => field?.toLowerCase().includes(term)),
			)
		: allUsers;

	const invalidateList = () =>
		queryClient.invalidateQueries({
			queryKey: adminListUsersQueryKey({ query: { size: PAGE_SIZE } }),
		});

	const updateRole = useMutation({
		...adminUpdateUserMutation(),
		onSuccess: async (_data, variables) => {
			await invalidateList();
			toast.success(`Role updated to ${variables.body.appRole}.`);
			setRoleTarget(null);
		},
		// Errors (e.g. the last-admin 409) are surfaced inline in the dialog, which stays open so the
		// blocked action and its reason sit together — not a detached toast that auto-dismisses.
	});

	const impersonate = useMutation({
		...impersonateMutation(),
		// Errors surfaced inline in the dialog (see updateRole).
	});

	const forceSignOut = useMutation({
		...adminRevokeUserSessionsMutation(),
		onSuccess: (data) => {
			const count = data?.revoked ?? 0;
			toast.success(
				count === 0
					? "No active sessions to sign out."
					: `Signed out — revoked ${count} session${count === 1 ? "" : "s"}.`,
			);
			setSignOutTarget(null);
		},
		onError: (error) => {
			toast.error(problemDetailOf(error, "Couldn't sign the user out."));
			setSignOutTarget(null);
		},
	});

	const handleConfirmSignOut = () => {
		const id = signOutTarget?.user.id;
		if (id == null) return;
		forceSignOut.mutate({ path: { id } });
	};

	const handleConfirmRole = (user: AdminAccountView, nextRole: string) => {
		if (user.id == null) return;
		updateRole.mutate({ path: { id: user.id }, body: { appRole: nextRole } });
	};

	const handleConfirmImpersonate = (user: AdminAccountView, reason: string) => {
		if (user.id == null) return;
		impersonate.mutate(
			{ body: { targetAccountId: user.id, reason } },
			{
				onSuccess: () => {
					setImpersonateTarget(null);
					// Full navigation home re-resolves the session cookie + current-user, from which the
					// impersonation banner renders — the cleanest reset for an app-wide identity switch.
					window.location.assign("/");
				},
			},
		);
	};

	return (
		<div className="mx-auto w-full max-w-6xl space-y-6 py-6">
			<header className="space-y-1">
				<div className="flex items-center gap-2">
					<UserCog className="size-6 text-muted-foreground" aria-hidden />
					<h1 className="text-2xl font-semibold">Users</h1>
				</div>
				<p className="text-sm text-muted-foreground">
					Manage application accounts: change roles and impersonate users for support.
				</p>
			</header>

			<div className="relative w-full sm:max-w-sm">
				<Label htmlFor="admin-users-search" className="sr-only">
					Search users
				</Label>
				<Users className="absolute left-3 top-2.5 size-4 text-muted-foreground" aria-hidden />
				<Input
					id="admin-users-search"
					type="search"
					placeholder="Search by name, email, role, or status…"
					value={search}
					onChange={(event) => setSearch(event.target.value)}
					className="pl-9"
				/>
			</div>

			<AdminUsersTable
				users={filteredUsers}
				isLoading={listQuery.isLoading}
				isError={listQuery.isError}
				hasSearch={deferredSearch.trim().length > 0}
				totalLoaded={allUsers.length}
				currentUserId={currentUserId}
				hasNextPage={listQuery.hasNextPage}
				isFetchingNextPage={listQuery.isFetchingNextPage}
				onLoadMore={() => listQuery.fetchNextPage()}
				onChangeRole={(user) => {
					updateRole.reset(); // clear any prior error so a fresh dialog starts clean
					setRoleTarget({ user });
				}}
				onImpersonate={(user) => {
					impersonate.reset();
					setImpersonateTarget({ user });
				}}
				onForceSignOut={(user) => setSignOutTarget({ user })}
			/>

			<ChangeRoleDialog
				icon={roleTarget?.user.appRole === "APP_ADMIN" ? ShieldOff : ShieldCheck}
				user={roleTarget?.user ?? null}
				isPending={updateRole.isPending}
				errorMessage={
					updateRole.isError
						? problemDetailOf(updateRole.error, "Couldn't update the role.")
						: undefined
				}
				onOpenChange={(open) => {
					if (!open) {
						setRoleTarget(null);
						updateRole.reset();
					}
				}}
				onConfirm={handleConfirmRole}
			/>

			<ImpersonateDialog
				user={impersonateTarget?.user ?? null}
				isPending={impersonate.isPending}
				errorMessage={
					impersonate.isError
						? problemDetailOf(impersonate.error, "Couldn't start impersonation.")
						: undefined
				}
				onOpenChange={(open) => {
					if (!open) {
						setImpersonateTarget(null);
						impersonate.reset();
					}
				}}
				onConfirm={handleConfirmImpersonate}
			/>

			<AlertDialog
				open={signOutTarget !== null}
				onOpenChange={(open) => {
					if (!open) {
						setSignOutTarget(null);
						forceSignOut.reset();
					}
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>
							Force sign-out {signOutTarget?.user.displayName ?? "this user"}?
						</AlertDialogTitle>
						<AlertDialogDescription>
							This revokes all of the account's active sessions immediately — they'll have to sign
							in again, and any in-progress impersonation of this account ends. This can't be
							undone.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>Cancel</AlertDialogCancel>
						<AlertDialogAction
							variant="destructive"
							disabled={forceSignOut.isPending}
							onClick={handleConfirmSignOut}
						>
							{forceSignOut.isPending ? "Signing out…" : "Force sign-out"}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
}
