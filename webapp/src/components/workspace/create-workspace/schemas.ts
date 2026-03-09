import { z } from "zod";

/** Step 1: GitLab connection details. */
export const connectionSchema = z.object({
	serverUrl: z
		.string()
		.transform((v) => v.trim())
		.pipe(
			z.union([
				z.literal(""),
				z.string().url("Must be a valid URL").startsWith("https://", "Must use HTTPS"),
			]),
		),
	personalAccessToken: z.string().min(1, "Personal access token is required"),
});
export type ConnectionFormData = z.infer<typeof connectionSchema>;

/** Step 3: Workspace display name and slug. */
export const workspaceDetailsSchema = z.object({
	displayName: z
		.string()
		.transform((v) => v.trim())
		.pipe(z.string().min(1, "Display name is required").max(100, "Display name is too long")),
	workspaceSlug: z
		.string()
		.regex(
			/^[a-z0-9][a-z0-9-]{2,50}$/,
			"Slug must be 3–51 characters, start with a lowercase letter or digit, and contain only lowercase letters, digits, or hyphens",
		),
});
export type WorkspaceDetailsData = z.infer<typeof workspaceDetailsSchema>;
