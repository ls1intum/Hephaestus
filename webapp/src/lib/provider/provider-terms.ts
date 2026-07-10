export type ProviderType = "GITHUB" | "GITLAB";

/** Lowercase slug used for the `data-provider` CSS attribute. */
export type ProviderSlug = Lowercase<ProviderType>;

export interface ProviderTerminology {
	readonly displayName: string;
	readonly pullRequest: string;
	readonly pullRequests: string;
	readonly pullRequestShort: string;
	readonly pullRequestsShort: string;
	readonly repository: string;
	readonly repositories: string;
	readonly organization: string;
}

const TERMS = {
	GITHUB: {
		displayName: "GitHub",
		pullRequest: "Pull Request",
		pullRequests: "Pull Requests",
		pullRequestShort: "PR",
		pullRequestsShort: "PRs",
		repository: "Repository",
		repositories: "Repositories",
		organization: "Organization",
	},
	GITLAB: {
		displayName: "GitLab",
		pullRequest: "Merge Request",
		pullRequests: "Merge Requests",
		pullRequestShort: "MR",
		pullRequestsShort: "MRs",
		repository: "Project",
		repositories: "Projects",
		organization: "Group",
	},
} as const satisfies Record<ProviderType, ProviderTerminology>;

export type ProviderTerm = keyof ProviderTerminology;

/** Returns the terminology map for the given provider. */
export function getProviderTerms(provider: ProviderType): ProviderTerminology {
	return TERMS[provider];
}

/** Converts a ProviderType to its lowercase slug for `data-provider` attributes. */
export function getProviderSlug(provider: ProviderType): ProviderSlug {
	return provider.toLowerCase() as ProviderSlug;
}

/**
 * Narrows a workspace's provider (which may be a non-SCM identity provider such as SLACK) to an SCM
 * {@link ProviderType} for SCM-only UI (repo terminology, PR/MR state icons, provider palette).
 * Non-SCM providers fall back to GITHUB so these surfaces render gracefully instead of crashing.
 */
export function toScmProviderType(provider: string | null | undefined): ProviderType {
	return provider === "GITLAB" ? "GITLAB" : "GITHUB";
}
