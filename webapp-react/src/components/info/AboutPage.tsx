import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Card,
	CardContent,
	CardDescription,
	CardHeader,
	CardTitle,
} from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import {
	AlertCircle,
	Code,
	Github,
	Globe,
	Hammer,
	MessageSquare,
	Sparkles,
	Users,
} from "lucide-react";

interface Contributor {
	id: number;
	login: string;
	name: string;
	avatarUrl: string;
	htmlUrl: string;
}

interface AboutPageProps {
	isPending: boolean;
	isError: boolean;
	error?: Error;
	otherContributors: Contributor[];
}

export function AboutPage({
	isPending,
	isError,
	otherContributors,
}: AboutPageProps) {
	// Project manager information is hardcoded since it never changes
	const projectManager = {
		id: 5898705,
		login: "felixtjdietrich",
		avatarUrl: "https://avatars.githubusercontent.com/u/5898705",
		htmlUrl: "https://github.com/felixtjdietrich",
	};

	return (
		<div className="max-w-4xl mx-auto">
			{/* Hero section with symbolic element */}
			<section className="mb-16 text-center">
				<div className="inline-flex items-center justify-center mb-6 p-4 rounded-full bg-secondary">
					<Hammer className="size-12 text-primary" />
				</div>
				<h1 className="text-4xl font-bold mb-6">About Hephaestus</h1>
				<p className="text-xl text-muted-foreground max-w-2xl mx-auto">
					Named after the Greek craftsman of the gods, we forge powerful tools
					that transform how software teams collaborate, learn, and excel
					together.
				</p>
			</section>

			{/* Mission section - clear, direct and meaningful */}
			<section className="mb-20">
				<div className="mb-12">
					<Badge className="mb-4" variant="outline">
						Our Purpose
					</Badge>
					<h2 className="text-3xl font-bold mb-6">The Mission</h2>

					<div className="space-y-6">
						<p className="text-lg leading-relaxed">
							We empower novice software engineers and foster sustainable,
							collaborative development practices through smart gamification and
							AI guidance that adapts to your team's unique challenges.
						</p>

						<div className="border-l-4 border-primary pl-6 py-2">
							<p className="text-lg font-medium">
								We believe the best software isn't just about writing code —
								it's about building teams that bring out the best in each other.
							</p>
						</div>
					</div>
				</div>

				<div className="grid grid-cols-1 md:grid-cols-2 gap-8 mt-10">
					<Card className="bg-gradient-to-br from-background to-muted/50 border-muted">
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<Code className="h-5 w-5 text-primary" />
								<Badge variant="secondary">Core Feature</Badge>
							</div>
							<CardTitle>Code Review Gamification</CardTitle>
							<CardDescription>
								Turning technical work into team growth
							</CardDescription>
						</CardHeader>
						<CardContent>
							<p className="text-muted-foreground">
								Transform code reviews into engaging experiences with dynamic
								leaderboards, team competitions, and a structured league system
								that recognizes excellence and encourages participation from
								developers at all skill levels.
							</p>
						</CardContent>
					</Card>

					<Card className="bg-gradient-to-br from-background to-muted/50 border-muted">
						<CardHeader>
							<div className="flex items-center gap-2 mb-2">
								<Sparkles className="h-5 w-5 text-primary" />
								<Badge variant="secondary">Core Feature</Badge>
							</div>
							<CardTitle>AI-Powered Mentorship</CardTitle>
							<CardDescription>Your team's personalized guide</CardDescription>
						</CardHeader>
						<CardContent>
							<p className="text-muted-foreground">
								Receive contextual guidance through our AI mentor that provides
								personalized feedback, identifies growth opportunities, and
								helps team members develop their skills with practical insights
								that lead to measurable improvement.
							</p>
						</CardContent>
					</Card>
				</div>
			</section>

			<Separator className="my-16" />

			{/* Team section - clean, focused and personal */}
			<section className="mb-20">
				<Badge className="mb-4" variant="outline">
					Our People
				</Badge>
				<h2 className="text-3xl font-bold mb-10">The Team</h2>

				{/* Project Lead - elegant and simple card */}
				<div className="bg-gradient-to-br from-background to-muted/30 rounded-lg p-8 mb-16 border border-muted">
					<div className="flex flex-col md:flex-row gap-8 items-center md:items-start">
						<Avatar className="h-32 w-32 border-4 border-background">
							<AvatarImage
								src={projectManager.avatarUrl}
								alt={`${projectManager.login}'s avatar`}
							/>
							<AvatarFallback className="text-2xl">FD</AvatarFallback>
						</Avatar>
						<div className="space-y-4 text-center md:text-left">
							<div>
								<h3 className="text-2xl font-bold">Felix T.J. Dietrich</h3>
								<p className="text-primary">Project Architect & Vision Lead</p>
							</div>
							<p className="text-muted-foreground">
								Forging Hephaestus from concept to reality, Felix combines
								technical mastery with a passion for creating tools that empower
								software teams to achieve their full potential through
								data-driven insights and collaborative learning.
							</p>
							<div className="flex items-center gap-2 pt-2 justify-center md:justify-start">
								<Button variant="outline" size="sm" asChild>
									<a
										href={projectManager.htmlUrl}
										target="_blank"
										rel="noopener noreferrer"
									>
										<Github className="h-5 w-5" /> GitHub
									</a>
								</Button>
								<Button variant="outline" size="sm" asChild>
									<a
										href="https://aet.cit.tum.de/people/dietrich/"
										target="_blank"
										rel="noopener noreferrer"
									>
										<Globe className="h-5 w-5" />
										Website
									</a>
								</Button>
							</div>
						</div>
					</div>
				</div>

				{/* Contributors - clean grid with meaningful states */}
				<div className="space-y-6">
					<div className="flex items-center gap-2 mb-4">
						<Users className="h-5 w-5 text-primary" />
						<h3 className="text-xl font-bold">Contributors</h3>
					</div>
					<p className="text-muted-foreground mb-8">
						These talented individuals have contributed their skills to help
						shape Hephaestus into what it is today. Each contributor brings
						unique expertise that strengthens our platform.
					</p>

					{/* Loading state - elegant skeletons */}
					{isPending && (
						<div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 gap-6">
							{Array.from({ length: 10 }).map((_, index) => (
								// biome-ignore lint/suspicious/noArrayIndexKey: Data is static and not user-generated
								<div key={index} className="flex flex-col items-center gap-2">
									<Skeleton className="h-20 w-20 rounded-full" />
									<Skeleton className="h-4 w-24" />
								</div>
							))}
						</div>
					)}

					{/* Error state - simple and informative */}
					{isError && (
						<div className="bg-gradient-to-br from-background to-muted/30 rounded-lg p-8 text-center border border-muted">
							<AlertCircle className="h-8 w-8 text-destructive mx-auto mb-4" />
							<h4 className="text-lg font-medium mb-2">
								Contributor Data Unavailable
							</h4>
							<p className="text-muted-foreground">
								We're having trouble reaching our contributor information. Our
								team is working on it—please check back soon!
							</p>
						</div>
					)}

					{/* Contributors display - clean and focused grid */}
					{!isPending && !isError && (
						<div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 gap-x-6 gap-y-8">
							{otherContributors.length > 0 ? (
								otherContributors.map((contributor) => (
									<Button
										key={contributor.id}
										variant="ghost"
										asChild
										className="h-auto"
									>
										<a
											href={contributor.htmlUrl}
											target="_blank"
											rel="noopener noreferrer"
											className="flex flex-col items-center group"
										>
											<Avatar className="size-20">
												<AvatarImage
													src={contributor.avatarUrl}
													alt={`${contributor.login}'s avatar`}
												/>
												<AvatarFallback>
													{contributor.login.slice(0, 2).toUpperCase()}
												</AvatarFallback>
											</Avatar>
											<div className="flex flex-col items-center">
												<div className="font-medium text-center">
													{contributor.name}
												</div>
												<div className="text-sm text-muted-foreground">
													@{contributor.login}
												</div>
											</div>
										</a>
									</Button>
								))
							) : (
								<div className="col-span-full bg-gradient-to-br from-background to-muted/30 rounded-lg p-8 text-center border border-muted/50">
									<MessageSquare className="h-8 w-8 text-primary mx-auto mb-4" />
									<h4 className="text-lg font-medium mb-2">
										Join Our Community
									</h4>
									<p className="text-muted-foreground mb-6">
										Our forge is warming up! Be the first to join our
										contributor community and help shape the future of
										Hephaestus.
									</p>
									<Button asChild>
										<a
											href="https://github.com/ls1intum/Hephaestus"
											target="_blank"
											rel="noopener noreferrer"
											className="inline-flex items-center gap-2"
										>
											<Github className="h-4 w-4" />
											<span>Join Us on GitHub</span>
										</a>
									</Button>
								</div>
							)}
						</div>
					)}
				</div>
			</section>

			{/* Call to action - simple but effective */}
			<section className="mt-20 mb-8 bg-gradient-to-br from-background to-muted/30 rounded-lg p-8 text-center border border-muted/50">
				<Badge className="mb-4" variant="outline">
					Get Involved
				</Badge>
				<h2 className="text-3xl font-bold mb-4">
					Ready to Forge Something Great?
				</h2>
				<p className="text-muted-foreground max-w-2xl mx-auto mb-8">
					Hephaestus thrives on community contributions from developers of all
					skill levels. Whether you're fixing bugs, adding features, or
					improving documentation, your expertise helps shape our platform.
				</p>
				<div className="flex flex-col sm:flex-row gap-4 justify-center">
					<Button asChild variant="default" size="lg">
						<a
							href="https://github.com/ls1intum/Hephaestus"
							target="_blank"
							rel="noopener noreferrer"
							className="inline-flex items-center gap-2"
						>
							<Github className="h-4 w-4" />
							<span>GitHub Repository</span>
						</a>
					</Button>
					<Button asChild variant="outline" size="lg">
						<a
							href="https://ls1intum.github.io/Hephaestus/"
							target="_blank"
							rel="noopener noreferrer"
						>
							<span>View Documentation</span>
						</a>
					</Button>
				</div>
			</section>
		</div>
	);
}
