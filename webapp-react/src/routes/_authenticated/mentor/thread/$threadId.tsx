import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/mentor/thread/$threadId")(
	{
		component: RouteComponent,
	},
);

function RouteComponent() {
	return <div>Hello "/_authenticated/mentor/thread/thread"!</div>;
}
