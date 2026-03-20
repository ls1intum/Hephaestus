import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { useFeatureFlag } from "@/integrations/feature-flags";

export const Route = createFileRoute("/_authenticated/mentor/_mentor_access")({
	component: MentorLayout,
});

function MentorLayout() {
	const { enabled: hasMentorAccess, isLoading, isError } = useFeatureFlag("MENTOR_ACCESS");

	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}

	if (isError) {
		return (
			<div className="flex items-center justify-center h-96">
				<p className="text-muted-foreground">Failed to check access. Please try again later.</p>
			</div>
		);
	}

	if (!hasMentorAccess) {
		redirect({ to: "/", throw: true });
	}

	return <Outlet />;
}
