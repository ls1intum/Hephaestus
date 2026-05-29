import { MoreHorizontal, UserCog, Users } from "lucide-react";
import type { AdminAccountView } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Spinner } from "@/components/ui/spinner";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";

export interface AdminUsersTableProps {
	users: AdminAccountView[];
	isLoading: boolean;
	isError: boolean;
	/** Whether a search term is currently narrowing the visible rows. */
	hasSearch: boolean;
	/** Total rows loaded from the server (before client-side search filtering). */
	totalLoaded: number;
	/** Id of the signed-in admin — used to disable self-impersonation. Undefined until loaded. */
	currentUserId: number | undefined;
	hasNextPage: boolean;
	isFetchingNextPage: boolean;
	onLoadMore: () => void;
	onChangeRole: (user: AdminAccountView) => void;
	onImpersonate: (user: AdminAccountView) => void;
}

const COLUMN_COUNT = 6;

function roleBadgeVariant(appRole: string | undefined) {
	return appRole === "APP_ADMIN" ? "default" : "secondary";
}

function statusBadgeVariant(status: string | undefined) {
	if (!status) return "outline" as const;
	const normalized = status.toUpperCase();
	if (normalized === "ACTIVE") return "secondary" as const;
	if (normalized === "DISABLED" || normalized === "SUSPENDED") return "destructive" as const;
	return "outline" as const;
}

export function AdminUsersTable({
	users,
	isLoading,
	isError,
	hasSearch,
	totalLoaded,
	currentUserId,
	hasNextPage,
	isFetchingNextPage,
	onLoadMore,
	onChangeRole,
	onImpersonate,
}: AdminUsersTableProps) {
	return (
		<div className="space-y-4">
			<div className="rounded-md border">
				<Table aria-label="Application users">
					<TableHeader>
						<TableRow>
							<TableHead scope="col">ID</TableHead>
							<TableHead scope="col">Name</TableHead>
							<TableHead scope="col">Email</TableHead>
							<TableHead scope="col">Role</TableHead>
							<TableHead scope="col">Status</TableHead>
							<TableHead scope="col" className="text-right">
								<span className="sr-only">Actions</span>
							</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{isLoading ? (
							<TableRow>
								<TableCell colSpan={COLUMN_COUNT} className="h-32 text-center">
									<div className="flex flex-col items-center justify-center gap-2">
										<Spinner />
										<p className="text-sm text-muted-foreground">Loading users…</p>
									</div>
								</TableCell>
							</TableRow>
						) : isError ? (
							<TableRow>
								<TableCell colSpan={COLUMN_COUNT} className="h-32 text-center">
									<p className="text-sm text-destructive">
										Failed to load users. Please try again later.
									</p>
								</TableCell>
							</TableRow>
						) : users.length === 0 ? (
							<TableRow>
								<TableCell colSpan={COLUMN_COUNT} className="h-32 text-center">
									<div className="flex flex-col items-center justify-center gap-2">
										<Users className="size-8 text-muted-foreground" aria-hidden />
										<p className="text-sm font-medium">No users found</p>
										<p className="text-xs text-muted-foreground">
											{hasSearch ? "Try adjusting your search." : "No accounts exist yet."}
										</p>
									</div>
								</TableCell>
							</TableRow>
						) : (
							users.map((user) => {
								const isSelf = user.id != null && user.id === currentUserId;
								const name = user.displayName ?? "—";
								return (
									<TableRow key={user.id ?? user.primaryEmail}>
										<TableCell className="font-mono text-xs text-muted-foreground">
											{user.id ?? "—"}
										</TableCell>
										<TableCell className="font-medium">{name}</TableCell>
										<TableCell className="text-muted-foreground">
											{user.primaryEmail ?? "—"}
										</TableCell>
										<TableCell>
											<Badge variant={roleBadgeVariant(user.appRole)}>
												{user.appRole ?? "USER"}
											</Badge>
										</TableCell>
										<TableCell>
											{user.status ? (
												<Badge variant={statusBadgeVariant(user.status)}>{user.status}</Badge>
											) : (
												<span className="text-muted-foreground">—</span>
											)}
										</TableCell>
										<TableCell className="text-right">
											<DropdownMenu>
												<DropdownMenuTrigger
													render={
														<Button
															variant="ghost"
															size="icon-xs"
															aria-label={`Actions for ${name}`}
														/>
													}
												>
													<MoreHorizontal className="size-4" />
												</DropdownMenuTrigger>
												<DropdownMenuContent align="end">
													<DropdownMenuLabel>Account actions</DropdownMenuLabel>
													<DropdownMenuItem onClick={() => onChangeRole(user)}>
														<UserCog className="size-4" />
														{user.appRole === "APP_ADMIN" ? "Revoke admin" : "Change role"}
													</DropdownMenuItem>
													<DropdownMenuSeparator />
													<DropdownMenuItem
														disabled={isSelf}
														onClick={() => !isSelf && onImpersonate(user)}
													>
														<Users className="size-4" />
														{isSelf ? "Cannot impersonate self" : "Impersonate"}
													</DropdownMenuItem>
												</DropdownMenuContent>
											</DropdownMenu>
										</TableCell>
									</TableRow>
								);
							})
						)}
					</TableBody>
				</Table>
			</div>

			<div className="flex flex-col items-center justify-between gap-2 sm:flex-row">
				<p className="text-sm text-muted-foreground" aria-live="polite">
					Showing {users.length}
					{hasSearch ? ` of ${totalLoaded} loaded` : ""} user{users.length === 1 ? "" : "s"}
				</p>
				{hasNextPage && (
					<Button variant="outline" size="sm" onClick={onLoadMore} disabled={isFetchingNextPage}>
						{isFetchingNextPage ? <Spinner className="size-4" /> : null}
						Load more
					</Button>
				)}
			</div>
		</div>
	);
}
