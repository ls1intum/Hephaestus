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
						Feedback where you already work
					</h2>
					<p className="text-muted-foreground text-lg">
						Hephaestus runs from the tools your team already uses, so nobody has to go looking for
						feedback
					</p>
				</div>

				<div className="grid gap-8 md:grid-cols-2">
					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<GitPullRequest className="h-5 w-5 text-primary" />
							</div>
							<CardTitle>Practice feedback on pull requests and issues</CardTitle>
							<CardDescription>
								Reviews your work against real engineering practices
							</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Works with GitHub and GitLab repositories</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>
										Names what was done well, what could be better, and a way to get there
									</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Posts feedback as comments right on the pull request or issue</span>
								</li>
							</ul>
						</CardContent>
					</Card>

					<Card>
						<CardHeader>
							<div className="flex items-center gap-2 mb-2 text-muted-foreground">
								<MentorIcon className="-m-2" size={32} pad={4} />
							</div>
							<CardTitle>Heph, your AI mentor</CardTitle>
							<CardDescription>A mentor chat grounded in your repository activity</CardDescription>
						</CardHeader>
						<CardContent>
							<ul className="space-y-2">
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Knows your recent issues, commits, reviews, and pull requests</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Answers questions about your work and suggests next steps</span>
								</li>
								<li className="flex gap-2 items-start">
									<CheckCheck className="size-5 mt-1 text-provider-success-foreground" />
									<span>Also available in Slack, right where your team talks</span>
								</li>
							</ul>
						</CardContent>
					</Card>
				</div>
			</div>
		</section>
	);
}
