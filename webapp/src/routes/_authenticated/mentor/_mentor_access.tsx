import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth";

export const Route = createFileRoute("/_authenticated/mentor/_mentor_access")({
	component: MentorLayout,
});

function MentorLayout() {
	const { hasRole, isLoading } = useAuth();

	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner className="size-8" />
			</div>
		);
	}

	if (hasRole("mentor_access") === false) {
		redirect({ to: "/", throw: true });
	}

	return <Outlet />;
}
