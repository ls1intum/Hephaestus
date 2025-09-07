import type { Contributor } from "@/components/shared/ContributorGrid";
import { Badge } from "@/components/ui/badge";
import { ContributorSection } from "./ContributorSection";
import { type ProjectManager, ProjectManagerCard } from "./ProjectManagerCard";

interface AboutTeamSectionProps {
	projectManager: ProjectManager;
	contributors: Contributor[];
	isContributorsLoading: boolean;
	isContributorsError: boolean;
}

export function AboutTeamSection({
	projectManager,
	contributors,
	isContributorsLoading,
	isContributorsError,
}: AboutTeamSectionProps) {
	return (
		<section>
			<Badge className="mb-4" variant="outline">
				Our People
			</Badge>
			<h2 className="text-3xl font-bold mb-10">The Team</h2>
			<ProjectManagerCard projectManager={projectManager} />
			<ContributorSection
				contributors={contributors}
				isLoading={isContributorsLoading}
				isError={isContributorsError}
			/>
		</section>
	);
}
