import { createFileRoute } from "@tanstack/react-router";

import { ComingSoon } from "@/components/shared/ComingSoon";

export const Route = createFileRoute("/_authenticated/mentor")({
	component: RouteComponent,
});

function RouteComponent() {
	return (
		<div className="h-1/2 flex items-center justify-center">
			<ComingSoon />
		</div>
	);
}
