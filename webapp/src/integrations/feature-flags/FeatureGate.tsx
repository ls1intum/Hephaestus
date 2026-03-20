import type { ReactNode } from "react";
import { type FeatureFlagName, useFeatureFlag } from "./hooks";

interface FeatureGateProps {
	/** The feature flag to check */
	flag: FeatureFlagName;
	/** Content to render when the flag is enabled */
	children: ReactNode;
	/** Optional content to render when the flag is disabled (default: null) */
	fallback?: ReactNode;
	/** Optional content to render while loading (default: null) */
	loading?: ReactNode;
}

/**
 * Conditionally renders children based on a feature flag.
 *
 * @example
 * ```tsx
 * <FeatureGate flag="GITLAB_WORKSPACE_CREATION">
 *   <CreateGitLabWorkspaceButton />
 * </FeatureGate>
 *
 * <FeatureGate
 *   flag="MENTOR_ACCESS"
 *   fallback={<UpgradePrompt />}
 *   loading={<Spinner />}
 * >
 *   <MentorChat />
 * </FeatureGate>
 * ```
 */
export function FeatureGate({ flag, children, fallback = null, loading = null }: FeatureGateProps) {
	const { enabled, isLoading } = useFeatureFlag(flag);

	if (isLoading) return <>{loading}</>;
	if (!enabled) return <>{fallback}</>;
	return <>{children}</>;
}
