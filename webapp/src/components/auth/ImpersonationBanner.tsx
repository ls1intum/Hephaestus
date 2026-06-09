import { useMutation } from "@tanstack/react-query";
import { useEffect } from "react";
import { toast } from "sonner";
import { exitImpersonationMutation } from "@/api/@tanstack/react-query.gen";
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
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth/AuthContext";
import { cn } from "@/lib/utils";
import { useImpersonationStore } from "@/stores/impersonation-store";

/**
 * Persistent banner shown across the top of the app while the current session is impersonating
 * another account. Reads impersonation state from the current user (via `useAuth`); renders nothing
 * when not impersonating, so it is safe to always mount.
 *
 * Impersonation is read-only by default (the server's `ImpersonationGuard` 403s writes). "Enable
 * writes" is a deliberate second confirmation that flips the in-memory write-mode flag the request
 * interceptor reads (see `main.tsx` + `impersonation-store`); the banner turns red while writes are
 * enabled so the elevated state is unmistakable. "Stop impersonating" exits via the server mutation
 * and reloads so the operator session re-resolves.
 */
export function ImpersonationBanner() {
	const { isImpersonating, impersonatedDisplayName } = useAuth();
	const writesEnabled = useImpersonationStore((s) => s.writesEnabled);
	const setWritesEnabled = useImpersonationStore((s) => s.setWritesEnabled);

	const exit = useMutation({
		...exitImpersonationMutation(),
		onSuccess: () => {
			// Full reload so the restored operator session cookie + current-user re-resolve cleanly.
			window.location.assign("/");
		},
		onError: (error) => {
			// A failed exit must be loud: the operator is still impersonating. Disarm write-mode so a
			// stuck session can't keep mutating, and tell them to retry (rather than silently re-enabling).
			console.error("Failed to exit impersonation:", error);
			setWritesEnabled(false);
			toast.error("Could not stop impersonating. Please try again.");
		},
	});

	// Expose a global CSS hook while impersonating, and force write-mode back off whenever
	// impersonation is not active (defence in depth alongside the reload-on-exit reset).
	useEffect(() => {
		if (!isImpersonating) {
			setWritesEnabled(false);
			return;
		}
		document.body.setAttribute("data-impersonating", "true");
		return () => {
			document.body.removeAttribute("data-impersonating");
			setWritesEnabled(false);
		};
	}, [isImpersonating, setWritesEnabled]);

	if (!isImpersonating) {
		return null;
	}

	const displayName = impersonatedDisplayName ?? "another account";

	return (
		<div
			role="status"
			aria-live="polite"
			className={cn(
				"sticky top-0 z-50 flex w-full items-center justify-center gap-x-3 gap-y-1 flex-wrap border-b px-4 py-2 text-sm",
				writesEnabled
					? "border-red-300 bg-red-100 text-red-900 dark:border-red-800 dark:bg-red-950 dark:text-red-100"
					: "border-amber-300 bg-amber-100 text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100",
			)}
		>
			<span>
				Impersonating <strong className="font-semibold">{displayName}</strong>
				<span className="mx-2 opacity-60">·</span>
				{writesEnabled ? <strong className="font-semibold">writes enabled</strong> : "read-only"}
			</span>

			{writesEnabled ? (
				<Button
					variant="outline"
					size="sm"
					onClick={() => setWritesEnabled(false)}
					className="h-7 border-red-400 bg-transparent text-red-900 hover:bg-red-200 dark:border-red-700 dark:text-red-100 dark:hover:bg-red-900"
				>
					Disable writes
				</Button>
			) : (
				<AlertDialog>
					<AlertDialogTrigger
						render={
							<Button
								variant="outline"
								size="sm"
								className="h-7 border-amber-400 bg-transparent text-amber-900 hover:bg-amber-200 dark:border-amber-700 dark:text-amber-100 dark:hover:bg-amber-900"
							/>
						}
					>
						Enable writes
					</AlertDialogTrigger>
					<AlertDialogContent>
						<AlertDialogHeader>
							<AlertDialogTitle>Make changes as {displayName}?</AlertDialogTitle>
							<AlertDialogDescription>
								You are impersonating <strong>{displayName}</strong>. Enabling writes lets you
								create, edit, and delete <em>as this user</em>. Every change is attributed to you in
								the audit log. Writes turn off automatically when you stop impersonating or reload.
							</AlertDialogDescription>
						</AlertDialogHeader>
						<AlertDialogFooter>
							<AlertDialogCancel>Cancel</AlertDialogCancel>
							<AlertDialogAction variant="destructive" onClick={() => setWritesEnabled(true)}>
								Enable writes
							</AlertDialogAction>
						</AlertDialogFooter>
					</AlertDialogContent>
				</AlertDialog>
			)}

			<Button
				variant="outline"
				size="sm"
				disabled={exit.isPending}
				onClick={() => exit.mutate({})}
				aria-label="Stop impersonating and restore your account"
				className={cn(
					"h-7 bg-transparent",
					writesEnabled
						? "border-red-400 text-red-900 hover:bg-red-200 dark:border-red-700 dark:text-red-100 dark:hover:bg-red-900"
						: "border-amber-400 text-amber-900 hover:bg-amber-200 dark:border-amber-700 dark:text-amber-100 dark:hover:bg-amber-900",
				)}
			>
				{exit.isPending ? <Spinner className="mr-2 size-3.5" /> : null}
				Stop impersonating
			</Button>
		</div>
	);
}
