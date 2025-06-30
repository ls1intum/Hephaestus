import { Spinner } from "@/components/ui/spinner";
import { useAuth } from "@/integrations/auth";
import { Outlet, createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/mentor/_mentor_access")({
	component: MentorLayout,
});

function MentorLayout() {
	const { hasRole, isLoading } = useAuth();

	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-96">
				<Spinner size="lg" />
			</div>
		);
	}

	if (hasRole("mentor_access") === false) {
		redirect({ to: "/", throw: true });
	}

	return <Outlet />;
}
