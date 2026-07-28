import { CheckCheck, GitPullRequest } from "lucide-react";
import { MentorIcon } from "@/components/mentor/MentorIcon";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export function LandingFeaturesSection() {
	return (
		<section id="features" className="w-full py-12 md:py-24 bg-background">
			<div className="container px-4 md:px-6">
				<div className="mb-12 text-center max-w-3xl mx-auto">
					<Badge className="mb-4" variant="outline">
						Key features
					</Badge>
					<h2 className="text-3xl font-bold tracking-tighter sm:text-4xl mb-4">
						Two ways to get feedback
					</h2>
					<p className="text-muted-foreground text-lg">
						Read practice feedback on GitHub or GitLab, then talk it through with Heph when you want
						a broader view.
					</p>
				</div>

				<div className="grid gap-8 md:grid-cols-2">
					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<GitPullRequest className="h-5 w-5 text-primary" />
							</div>
							<CardTitle>Practice feedback</CardTitle>
							<CardDescription>Specific feedback on how the work was done</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Checks the engineering practices your workspace has chosen</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Points to evidence and explains what worked or what could improve</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Posts a comment with a practical next step</span>
								</li>
							</ul>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2 text-muted-foreground">
								<MentorIcon className="-m-2" size={32} pad={4} />
							</div>
							<CardTitle>Chat with Heph</CardTitle>
							<CardDescription>Talk through feedback and recent work</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Can use your recent work and delivered feedback as context</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Helps you reflect before deciding what to do next</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Available in the web app and, when connected, in Slack</span>
								</li>
							</ul>
						</CardContent>
					</Card>
				</div>
			</div>
		</section>
	);
}
