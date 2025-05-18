import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/workspace/users")({
	component: RouteComponent,
});

function RouteComponent() {
	return <div>Hello "/_authenticated/workspace/users"!</div>;
}
