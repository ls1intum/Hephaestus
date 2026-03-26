import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/user/$username/best-practices",
)({
	component: BestPracticesPlaceholder,
});

function BestPracticesPlaceholder() {
	return (
		<div className="flex items-center justify-center py-24">
			<p className="text-muted-foreground text-lg">Practices view coming soon.</p>
		</div>
	);
}
