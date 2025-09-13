import { Github, Globe } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";

export interface ProjectManager {
	id: number;
	login: string;
	avatarUrl: string;
	htmlUrl: string;
	name: string;
	title: string;
	description: string;
	websiteUrl: string;
}

interface ProjectManagerCardProps {
	projectManager: ProjectManager;
}

export function ProjectManagerCard({
	projectManager,
}: ProjectManagerCardProps) {
	return (
		<div className="bg-gradient-to-br from-background to-muted/30 rounded-lg p-8 mb-16 border border-muted">
			<div className="flex flex-col md:flex-row gap-8 items-center md:items-start">
				<Avatar className="h-32 w-32 border-4 border-background">
					<AvatarImage
						src={projectManager.avatarUrl}
						alt={`${projectManager.login}'s avatar`}
					/>
					<AvatarFallback className="text-2xl">
						{projectManager.name
							.split(" ")
							.map((n) => n[0])
							.join("")}
					</AvatarFallback>
				</Avatar>
				<div className="space-y-4 text-center md:text-left">
					<div>
						<h3 className="text-2xl font-bold">{projectManager.name}</h3>
						<p className="text-primary">{projectManager.title}</p>
					</div>
					<p className="text-muted-foreground">{projectManager.description}</p>
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
								href={projectManager.websiteUrl}
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
	);
}
