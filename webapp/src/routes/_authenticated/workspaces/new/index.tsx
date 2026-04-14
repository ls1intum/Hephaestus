import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import type { LucideIcon } from "lucide-react";
import { ArrowLeftIcon, GithubIcon, GitlabIcon, OctagonXIcon } from "lucide-react";
import { getProvidersOptions } from "@/api/@tanstack/react-query.gen";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";

export const Route = createFileRoute("/_authenticated/workspaces/new/")({
	component: ProviderSelectionPage,
});

interface Provider {
	id: string;
	name: string;
	description: string;
	icon: LucideIcon;
	to: "/workspaces/new/github" | "/workspaces/new/gitlab";
}

function ProviderSelectionPage() {
	const {
		data: workspaceProviders,
		isLoading,
		isError,
	} = useQuery({
		...getProvidersOptions(),
		staleTime: 5 * 60 * 1000,
	});

	const providers: Provider[] = [];
	if (workspaceProviders?.github) {
		providers.push({
			id: "github",
			name: "GitHub",
			description:
				"Install the Hephaestus GitHub App on your organization. Workspaces are created automatically.",
			icon: GithubIcon,
			to: "/workspaces/new/github",
		});
	}
	if (workspaceProviders?.gitlab) {
		providers.push({
			id: "gitlab",
			name: "GitLab",
			description: "Connect with an access token and select a group to monitor.",
			icon: GitlabIcon,
			to: "/workspaces/new/gitlab",
		});
	}

	return (
		<div className="mx-auto max-w-2xl py-8">
			<Link
				to="/"
				className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-6"
				aria-label="Back to dashboard"
			>
				<ArrowLeftIcon className="size-3.5" />
				Back
			</Link>
			<div className="space-y-1.5 mb-8">
				<h1 className="text-2xl font-semibold tracking-tight">Create Workspace</h1>
				<p className="text-muted-foreground">Choose your Git provider to get started.</p>
			</div>
			{isError && (
				<Alert variant="destructive" className="mb-4">
					<OctagonXIcon aria-hidden="true" />
					<AlertTitle>Load failure</AlertTitle>
					<AlertDescription>
						Could not load provider options. Try refreshing the page.
					</AlertDescription>
				</Alert>
			)}
			{isLoading ? (
				<div className="flex justify-center py-12">
					<Spinner />
				</div>
			) : providers.length === 0 && !isError ? (
				<p className="text-center text-muted-foreground py-12">
					No providers are currently available. Contact your administrator.
				</p>
			) : (
				<div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
					{providers.map((provider) => (
						<Link
							key={provider.id}
							to={provider.to}
							aria-label={`Set up workspace with ${provider.name}`}
						>
							<Card className="h-full cursor-pointer transition-colors hover:bg-muted/50 hover:border-foreground/20">
								<CardHeader>
									<div className="flex items-center gap-3 mb-1">
										<provider.icon className="size-6" />
										<CardTitle className="text-lg">{provider.name}</CardTitle>
									</div>
									<CardDescription>{provider.description}</CardDescription>
								</CardHeader>
							</Card>
						</Link>
					))}
				</div>
			)}
		</div>
	);
}
