import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { CheckCircleIcon, XCircleIcon } from "lucide-react";
import { useEffect } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

type Search = {
	status?: "success" | "error";
	reason?: string;
};

/**
 * Landing route hit by the server-side OAuth callback after a vendor finalizes
 * a Connection. The callback controller redirects here with
 * {@code ?status=success} (or {@code ?status=error&reason=...}); we surface a toast
 * and bounce back to the originating workspace's admin settings, which we stashed
 * in {@code sessionStorage} before initiating OAuth.
 */
export const Route = createFileRoute("/_authenticated/integrations")({
	component: IntegrationsCallback,
	validateSearch: (search): Search => ({
		status: search.status === "success" || search.status === "error" ? search.status : undefined,
		reason: typeof search.reason === "string" ? search.reason : undefined,
	}),
});

function IntegrationsCallback() {
	const { status, reason } = Route.useSearch();
	const navigate = useNavigate();

	useEffect(() => {
		const slug = window.sessionStorage.getItem("slack-connect-return-slug");
		if (status === "success") {
			toast.success("Integration connected");
		} else if (status === "error") {
			toast.error("Integration connection failed", { description: reason });
		}
		window.sessionStorage.removeItem("slack-connect-return-slug");
		if (slug) {
			navigate({ to: "/w/$workspaceSlug/admin/settings", params: { workspaceSlug: slug } });
		}
	}, [status, reason, navigate]);

	const ok = status === "success";
	const Icon = ok ? CheckCircleIcon : XCircleIcon;
	return (
		<div className="mx-auto max-w-md py-12">
			<Card>
				<CardContent className="flex flex-col items-center gap-4 py-8">
					<Icon className={ok ? "size-12 text-green-600" : "size-12 text-destructive"} />
					<div className="text-center">
						<h1 className="text-xl font-semibold">
							{ok ? "Integration connected" : "Connection failed"}
						</h1>
						{!ok && reason && <p className="mt-2 text-sm text-muted-foreground">{reason}</p>}
					</div>
					<Button onClick={() => navigate({ to: "/" })}>Return to dashboard</Button>
				</CardContent>
			</Card>
		</div>
	);
}
