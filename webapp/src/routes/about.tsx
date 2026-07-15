import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { listGlobalContributorsOptions } from "@/api/@tanstack/react-query.gen";
import { AboutPage } from "@/components/info/about/AboutPage";
import type { ProjectManager } from "@/components/info/about/ProjectManagerCard";

export const Route = createFileRoute("/about")({
	component: AboutContainer,
});

const PROJECT_MANAGER_DATA: ProjectManager = {
	id: 5898705,
	login: "felixtjdietrich",
	name: "Felix T.J. Dietrich",
	title: "Project lead",
	description:
		"Felix started Hephaestus and leads its development as a doctoral researcher at TUM. He works on giving every developer the kind of mentoring feedback that usually only a few get.",
	avatarUrl: "https://avatars.githubusercontent.com/u/5898705",
	htmlUrl: "https://github.com/felixtjdietrich",
	websiteUrl: "https://aet.cit.tum.de/people/dietrich/",
};

function AboutContainer() {
	const {
		data: contributors = [],
		isPending,
		isError,
	} = useQuery({
		...listGlobalContributorsOptions(),
		gcTime: Number.POSITIVE_INFINITY,
	});

	// Filter out the project manager and bots
	const otherContributors =
		contributors?.filter(
			(contributor) =>
				contributor.id !== PROJECT_MANAGER_DATA.id && !contributor.login.includes("[bot]"),
		) || [];

	return (
		<AboutPage
			projectManager={PROJECT_MANAGER_DATA}
			isPending={isPending}
			isError={isError}
			otherContributors={otherContributors}
		/>
	);
}
