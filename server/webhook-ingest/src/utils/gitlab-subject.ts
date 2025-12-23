/**
 * Sanitize path parts for NATS subject tokens.
 * Replaces dots with "~" to avoid extra NATS tokens.
 */
function sanitizeParts(path: string): string[] {
	return path
		.split("/")
		.filter(Boolean)
		.map((part) => String(part).replace(/\./g, "~"));
}

/**
 * Extract namespace and project from project-scoped payload.
 */
function extractFromProject(payload: Record<string, unknown>): {
	namespace: string | null;
	project: string | null;
} {
	const project = (payload.project as Record<string, unknown>) || {};
	const pathWithNamespace =
		(payload.path_with_namespace as string) || (project.path_with_namespace as string);

	if (pathWithNamespace) {
		const parts = sanitizeParts(pathWithNamespace);
		if (parts.length >= 2) {
			return {
				namespace: parts.slice(0, -1).join("~"),
				project: parts[parts.length - 1] ?? "?",
			};
		}
	}
	return { namespace: null, project: null };
}

/**
 * Extract namespace from group-scoped payload.
 */
function extractFromGroup(payload: Record<string, unknown>): {
	namespace: string | null;
	project: string | null;
} {
	const group = (payload.group as Record<string, unknown>) || {};
	const groupPath =
		(group.full_path as string) || (group.path as string) || (group.group_path as string);

	if (groupPath) {
		const parts = sanitizeParts(groupPath);
		if (parts.length > 0) {
			return { namespace: parts.join("~"), project: "?" };
		}
	}
	return { namespace: null, project: null };
}

/**
 * Extract namespace and project from object_attributes URL.
 */
function extractFromObjectAttributes(payload: Record<string, unknown>): {
	namespace: string | null;
	project: string | null;
} {
	const objectAttributes = (payload.object_attributes as Record<string, unknown>) || {};
	const hasProject = objectAttributes.project_id !== undefined;
	const url = (objectAttributes.url as string) || "";

	if (!url.includes("://")) {
		return { namespace: null, project: null };
	}

	// Example: https://gitlab.lrz.de/ga84xah/codereviewtest/-/merge_requests/1#note_4108500
	let path = url.split("://")[1]?.split("/").slice(1).join("/") || "";
	if (path.includes("/-/")) {
		path = path.split("/-/")[0] ?? "";
	}
	const parts = sanitizeParts(path);

	if (hasProject && parts.length > 1) {
		return {
			namespace: parts.slice(0, -1).join("~"),
			project: parts[parts.length - 1] ?? "?",
		};
	}
	if (parts.length > 0) {
		return { namespace: parts.join("~"), project: "?" };
	}
	return { namespace: null, project: null };
}

/**
 * Build NATS subject for GitLab webhook payload.
 *
 * Follows the format: gitlab.<namespace>.<project>.<event_name>
 * with proper sanitization for NATS subject tokens.
 */
export function buildGitLabSubject(payload: Record<string, unknown>): string {
	const eventName = (
		(payload.object_kind as string) ||
		(payload.event_name as string) ||
		"unknown"
	).toLowerCase();

	// Try extraction strategies in order of priority
	let result = extractFromProject(payload);

	if (!result.namespace) {
		result = extractFromGroup(payload);
	}

	if (!result.namespace) {
		result = extractFromObjectAttributes(payload);
	}

	// Fallback to instance-level
	const namespaceSegment = result.namespace ?? "?";
	const projectSegment = result.project ?? "?";

	return ["gitlab", namespaceSegment, projectSegment, eventName].join(".");
}
