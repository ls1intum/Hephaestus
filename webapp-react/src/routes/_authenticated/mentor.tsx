import { createFileRoute } from "@tanstack/react-router";

import { Playground } from "@/components/mentor/Playground";
import { ComingSoon } from "@/components/shared/ComingSoon";
import { useAuth } from "@/integrations/auth";

export const Route = createFileRoute("/_authenticated/mentor")({
	component: RouteComponent,
});

function RouteComponent() {
	const { hasRole } = useAuth();

	if (!hasRole("mentor_access")) {
		return (
			<div className="h-1/2 flex items-center justify-center">
				<ComingSoon />
			</div>
		);
	}

	return <Playground />;
}
