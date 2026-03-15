import { createFileRoute, Link } from "@tanstack/react-router";
import type { LucideIcon } from "lucide-react";
import { ArrowLeftIcon, GithubIcon, GitlabIcon } from "lucide-react";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import environment from "@/environment";

export const Route = createFileRoute("/_authenticated/workspaces/new/")({
	component: ProviderSelectionPage,
});

interface Provider {
	id: string;
	name: string;
	description: string;
	icon: LucideIcon;
	to: string;
}

function getProviders(): Provider[] {
	const providers: Provider[] = [];
	if (environment.github.appUrl) {
		providers.push({
			id: "github",
			name: "GitHub",
			description:
				"Install the Hephaestus GitHub App on your organization. Workspaces are created automatically.",
			icon: GithubIcon,
			to: "/workspaces/new/github",
		});
	}
	providers.push({
		id: "gitlab",
		name: "GitLab",
		description: "Connect with a Personal Access Token and select a group to monitor.",
		icon: GitlabIcon,
		to: "/workspaces/new/gitlab",
	});
	return providers;
}

function ProviderSelectionPage() {
	const providers = getProviders();

	return (
		<div className="mx-auto max-w-2xl py-8">
			<Link
				to="/"
				className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-6"
			>
				<ArrowLeftIcon className="size-3.5" />
				Back
			</Link>
			<div className="space-y-1.5 mb-8">
				<h1 className="text-2xl font-semibold tracking-tight">Create Workspace</h1>
				<p className="text-muted-foreground">Choose your Git provider to get started.</p>
			</div>
			<div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
				{providers.map((provider) => (
					<Link key={provider.id} to={provider.to}>
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
		</div>
	);
}
