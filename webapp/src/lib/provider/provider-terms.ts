export type ProviderType = "GITHUB" | "GITLAB";

interface ProviderTerminology {
	readonly pullRequest: string;
	readonly pullRequests: string;
	readonly pr: string;
	readonly prs: string;
	readonly repository: string;
	readonly repositories: string;
	readonly organization: string;
}

const TERMS = {
	GITHUB: {
		pullRequest: "Pull Request",
		pullRequests: "Pull Requests",
		pr: "PR",
		prs: "PRs",
		repository: "Repository",
		repositories: "Repositories",
		organization: "Organization",
	},
	GITLAB: {
		pullRequest: "Merge Request",
		pullRequests: "Merge Requests",
		pr: "MR",
		prs: "MRs",
		repository: "Project",
		repositories: "Projects",
		organization: "Group",
	},
} as const satisfies Record<ProviderType, ProviderTerminology>;

export type ProviderTerm = keyof (typeof TERMS)["GITHUB"];

/** Returns the terminology map for the given provider. */
export function getProviderTerms(provider: ProviderType) {
	return TERMS[provider];
}
