import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MonitorIcon } from "lucide-react";
import { toast } from "sonner";
import {
	listSessionsOptions,
	listSessionsQueryKey,
	revokeOtherSessionsMutation,
	revokeSessionMutation,
} from "@/api/@tanstack/react-query.gen";
import type { SessionView } from "@/api/types.gen";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

function formatTimestamp(value?: Date): string | undefined {
	if (!value) return undefined;
	const date = value instanceof Date ? value : new Date(value);
	if (Number.isNaN(date.getTime())) return undefined;
	return date.toLocaleString(undefined, {
		dateStyle: "medium",
		timeStyle: "short",
	});
}

/**
 * Settings section listing the account's active sessions (ADR 0017 native auth).
 *
 * The current session is badged and cannot be revoked from here; other sessions
 * can be revoked individually or all at once via "Sign out everywhere else".
 */
export function SessionsSection() {
	const queryClient = useQueryClient();

	const sessionsQuery = useQuery({ ...listSessionsOptions({}) });

	const invalidateSessions = () =>
		queryClient.invalidateQueries({ queryKey: listSessionsQueryKey() });

	const revokeOne = useMutation({
		...revokeSessionMutation(),
		onSuccess: () => {
			invalidateSessions();
			toast.success("Session revoked");
		},
		onError: (error) => {
			console.error("Failed to revoke session:", error);
			toast.error("Failed to revoke session. Please try again later.");
		},
	});

	const revokeOthers = useMutation({
		...revokeOtherSessionsMutation(),
		onSuccess: () => {
			invalidateSessions();
			toast.success("Signed out of all other sessions");
		},
		onError: (error) => {
			console.error("Failed to revoke other sessions:", error);
			toast.error("Failed to sign out other sessions. Please try again later.");
		},
	});

	const sessions: SessionView[] = sessionsQuery.data ?? [];
	const otherSessionCount = sessions.filter((s) => !s.current).length;

	return (
		<section className="space-y-4" aria-labelledby="sessions-heading">
			<div className="flex items-start justify-between gap-4">
				<div className="space-y-1">
					<h2 id="sessions-heading" className="text-xl font-semibold">
						Active Sessions
					</h2>
					<p className="text-sm text-muted-foreground">
						Devices and browsers currently signed in to your account.
					</p>
				</div>
				{otherSessionCount > 0 && (
					<AlertDialog>
						<AlertDialogTrigger
							render={
								<Button
									variant="outline"
									size="sm"
									disabled={revokeOthers.isPending}
									className="mt-1 shrink-0"
								>
									{revokeOthers.isPending ? <Spinner className="mr-1.5" /> : null}
									Sign out everywhere else
								</Button>
							}
						/>
						<AlertDialogContent>
							<AlertDialogHeader>
								<AlertDialogTitle>Sign out of all other sessions?</AlertDialogTitle>
								<AlertDialogDescription>
									This revokes every session except the one you are using now. Other devices will
									need to sign in again.
								</AlertDialogDescription>
							</AlertDialogHeader>
							<AlertDialogFooter>
								<AlertDialogCancel>Cancel</AlertDialogCancel>
								<AlertDialogAction
									onClick={() => revokeOthers.mutate({})}
									disabled={revokeOthers.isPending}
								>
									Sign out others
								</AlertDialogAction>
							</AlertDialogFooter>
						</AlertDialogContent>
					</AlertDialog>
				)}
			</div>

			{sessionsQuery.isLoading ? (
				<div className="flex justify-center py-6">
					<Spinner aria-label="Loading sessions" />
				</div>
			) : sessionsQuery.isError ? (
				<p className="text-sm text-destructive" role="alert">
					Failed to load sessions. Please try refreshing the page.
				</p>
			) : sessions.length === 0 ? (
				<p className="text-sm text-muted-foreground">No active sessions found.</p>
			) : (
				<div className="space-y-3">
					{sessions.map((session) => {
						const lastSeen = formatTimestamp(session.issuedAt);
						// Scope the pending state to the row actually being revoked so a single revoke
						// doesn't disable/spin every other session's button (mirrors LoginProvidersSettings).
						const isRevokingThis =
							revokeOne.isPending && revokeOne.variables?.path.jti === session.jti;
						return (
							<div
								key={session.jti ?? `${session.userAgent}:${session.ip}`}
								className="flex items-center justify-between gap-4 rounded-lg border p-4"
							>
								<div className="flex items-center gap-3 min-w-0">
									<MonitorIcon className="size-5 shrink-0" aria-hidden="true" />
									<div className="min-w-0">
										<div className="flex items-center gap-2">
											<span className="text-sm font-medium truncate">
												{session.userAgent || "Unknown device"}
											</span>
											{session.current && (
												<Badge variant="secondary" className="text-xs">
													This device
												</Badge>
											)}
										</div>
										<p className="text-xs text-muted-foreground truncate">
											{[session.ip, lastSeen && `signed in ${lastSeen}`]
												.filter(Boolean)
												.join(" · ") || "No session details available"}
										</p>
									</div>
								</div>

								{session.current ? (
									<Button variant="outline" size="sm" disabled aria-label="Current session">
										Current
									</Button>
								) : (
									<Button
										variant="outline"
										size="sm"
										disabled={isRevokingThis || !session.jti}
										onClick={() => session.jti && revokeOne.mutate({ path: { jti: session.jti } })}
										aria-label="Revoke this session"
									>
										{isRevokingThis ? <Spinner className="mr-1.5" /> : null}
										Revoke
									</Button>
								)}
							</div>
						);
					})}
				</div>
			)}
		</section>
	);
}
