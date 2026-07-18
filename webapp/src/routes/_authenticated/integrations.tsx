import { createFileRoute, redirect, useNavigate } from "@tanstack/react-router";
import { CheckCircleIcon, InfoIcon, XCircleIcon } from "lucide-react";
import { useEffect, useRef } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

type Search = {
	status?: "success" | "error";
	reason?: string;
};

// OAuth callback landing route. When sessionStorage has a return-slug from the
// initiating workspace, we redirect back BEFORE rendering (no Card flash) and
// surface the toast on the destination. When there's no slug (different tab,
// browser restart), we render the terminal Card with a return-to-dashboard button.
export const Route = createFileRoute("/_authenticated/integrations")({
	component: IntegrationsCallback,
	validateSearch: (search): Search => ({
		status: search.status === "success" || search.status === "error" ? search.status : undefined,
		reason: typeof search.reason === "string" ? search.reason : undefined,
	}),
	beforeLoad: ({ search }) => {
		if (typeof window === "undefined") return; // SSR no-op
		const slug = window.sessionStorage.getItem("slack-connect-return-slug");
		if (!slug) return; // fall through to terminal render
		window.sessionStorage.removeItem("slack-connect-return-slug");
		// Stash the status so the destination route can toast it after navigation.
		if (search.status) {
			window.sessionStorage.setItem("slack-connect-result", search.status);
			if (search.reason) window.sessionStorage.setItem("slack-connect-reason", search.reason);
		}
		// Must land on the page that renders AdminSlackNotificationSettings — it is the sole
		// consumer of the stashed keys above. Any other destination leaves them unread, so the
		// admin gets no feedback here and a stale toast the next time they open this page.
		throw redirect({
			to: "/w/$workspaceSlug/admin/integrations/slack",
			params: { workspaceSlug: slug },
		});
	},
});

function IntegrationsCallback() {
	const { status, reason } = Route.useSearch();
	const navigate = useNavigate();
	const toasted = useRef(false);

	useEffect(() => {
		if (toasted.current) return;
		toasted.current = true;
		if (status === "success") toast.success("Integration connected");
		else if (status === "error")
			toast.error("Integration connection failed", { description: reason });
	}, [status, reason]);

	// A bare /integrations visit (no ?status — e.g. the user reloaded after the
	// beforeLoad redirect already consumed the return-slug) is NOT a failure. Only an
	// explicit ?status=error renders the destructive card; missing status is a neutral
	// terminal state, and ?status=success is the connected confirmation.
	const failed = status === "error";
	const succeeded = status === "success";
	const Icon = failed ? XCircleIcon : succeeded ? CheckCircleIcon : InfoIcon;
	const iconClass = failed
		? "size-12 text-destructive"
		: succeeded
			? "size-12 text-success"
			: "size-12 text-muted-foreground";
	const title = failed
		? "Connection failed"
		: succeeded
			? "Integration connected"
			: "Nothing to show here";
	return (
		<div className="mx-auto max-w-md py-12">
			<Card>
				<CardContent className="flex flex-col items-center gap-4 py-8">
					<Icon className={iconClass} />
					<div className="text-center">
						<h1 className="text-xl font-semibold">{title}</h1>
						{failed && reason && <p className="mt-2 text-sm text-muted-foreground">{reason}</p>}
					</div>
					<Button onClick={() => navigate({ to: "/" })}>Return to dashboard</Button>
				</CardContent>
			</Card>
		</div>
	);
}
