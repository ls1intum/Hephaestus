import {
	createFileRoute,
	Navigate,
	Outlet,
	useMatchRoute,
	useNavigate,
} from "@tanstack/react-router";
import { Spinner } from "@/components/ui/spinner";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { NoWorkspace } from "@/components/workspace/NoWorkspace";
import { useActiveWorkspaceSlug } from "@/hooks/use-active-workspace";
import { useWorkspaceFeatures } from "@/hooks/use-workspace-features";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/_admin/ai/practice-detection",
)({
	component: PracticeDetectionLayout,
});

function PracticeDetectionLayout() {
	const { workspaceSlug, isLoading: isWorkspaceLoading } = useActiveWorkspaceSlug();
	const {
		practicesEnabled,
		isLoading: featuresLoading,
		isError: featuresError,
	} = useWorkspaceFeatures();
	const navigate = useNavigate();
	const matchRoute = useMatchRoute();

	if (!workspaceSlug && !isWorkspaceLoading) {
		return <NoWorkspace />;
	}

	// `practicesEnabled` defaults optimistically to true while loading and on error,
	// so a query error leaves it unconfirmed. Treat "errored" as not-enabled and fall
	// back to settings rather than render a page the workspace may not have enabled.
	const practicesConfirmedEnabled = !featuresLoading && !featuresError && practicesEnabled;
	if (workspaceSlug && !featuresLoading && !practicesConfirmedEnabled) {
		return <Navigate to="/w/$workspaceSlug/admin/settings" params={{ workspaceSlug }} replace />;
	}

	if (featuresLoading || !practicesEnabled || !workspaceSlug) {
		return (
			<div className="flex justify-center items-center h-64">
				<Spinner className="h-8 w-8" />
			</div>
		);
	}

	const isCatalog = Boolean(
		matchRoute({
			to: "/w/$workspaceSlug/admin/ai/practice-detection/catalog",
			fuzzy: true,
		}),
	);
	const activeTab = isCatalog ? "catalog" : "policy";

	return (
		<div className="container mx-auto max-w-3xl py-6 space-y-6">
			<div>
				<h1 className="text-3xl font-bold tracking-tight">Practice detection</h1>
				<p className="text-muted-foreground">
					Configure how automated practice reviews run and which practices are evaluated.
				</p>
			</div>

			<Tabs
				value={activeTab}
				onValueChange={(value) => {
					if (value === "catalog") {
						navigate({
							to: "/w/$workspaceSlug/admin/ai/practice-detection/catalog",
							params: { workspaceSlug },
						});
					} else {
						navigate({
							to: "/w/$workspaceSlug/admin/ai/practice-detection",
							params: { workspaceSlug },
						});
					}
				}}
			>
				<TabsList>
					<TabsTrigger value="policy">Policy</TabsTrigger>
					<TabsTrigger value="catalog">Catalog</TabsTrigger>
				</TabsList>
			</Tabs>

			<Outlet />
		</div>
	);
}
