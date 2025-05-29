import { getContributorsOptions } from "@/api/@tanstack/react-query.gen";
import { AboutPage } from "@/components/info/about/AboutPage";
import type { ProjectManager } from "@/components/info/about/ProjectManagerCard";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/about")({
	component: AboutContainer,
});

const PROJECT_MANAGER_DATA: ProjectManager = {
	id: 5898705,
	login: "felixtjdietrich",
	name: "Felix T.J. Dietrich",
	title: "Project Architect & Vision Lead",
	description:
		"Forging Hephaestus from concept to reality, Felix combines technical mastery with a passion for creating tools that empower software teams to achieve their full potential through data-driven insights and collaborative learning.",
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
		...getContributorsOptions({}),
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
