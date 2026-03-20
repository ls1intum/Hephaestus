import { useQuery } from "@tanstack/react-query";
import { getUserFeatures } from "@/api/sdk.gen";
import type { FeatureFlags } from "@/api/types.gen";
import { useAuth } from "@/integrations/auth/AuthContext";

/**
 * Feature flag name type derived from the generated OpenAPI types.
 * Adding a new flag to the backend FeatureFlag enum + FeatureFlagsDTO
 * and running `npm run openapi-ts` automatically updates this type.
 */
export type FeatureFlagName = keyof Required<FeatureFlags>;

type FeatureFlagsResponse = Record<FeatureFlagName, boolean>;

const FEATURE_FLAGS_QUERY_KEY = ["user", "features"] as const;

async function fetchFeatureFlags(): Promise<FeatureFlagsResponse> {
	const { data } = await getUserFeatures();
	if (!data) {
		throw new Error("Failed to fetch feature flags");
	}
	return data as FeatureFlagsResponse;
}

function useFeatureFlagsQuery() {
	const { isAuthenticated } = useAuth();

	return useQuery<FeatureFlagsResponse>({
		queryKey: FEATURE_FLAGS_QUERY_KEY,
		queryFn: fetchFeatureFlags,
		enabled: isAuthenticated,
		staleTime: 60_000,
		retry: 3,
	});
}

/**
 * Returns whether a specific feature flag is enabled for the current user.
 *
 * @example
 * ```tsx
 * const { enabled, isLoading } = useFeatureFlag("MENTOR_ACCESS");
 * if (isLoading) return <Spinner />;
 * if (!enabled) return <Navigate to="/" />;
 * ```
 */
export function useFeatureFlag(flag: FeatureFlagName) {
	const { data, isLoading, isError } = useFeatureFlagsQuery();

	return {
		enabled: data?.[flag] ?? false,
		isLoading,
		isError,
	};
}

/**
 * Returns the full feature flags map for the current user.
 *
 * @example
 * ```tsx
 * const { flags, isLoading } = useFeatureFlags();
 * if (flags?.ADMIN) { ... }
 * ```
 */
export function useFeatureFlags() {
	const { data, isLoading, isError } = useFeatureFlagsQuery();

	return {
		flags: data,
		isLoading,
		isError,
	};
}

/**
 * Returns whether ALL of the specified flags are enabled (AND composition).
 *
 * @example
 * ```tsx
 * const { enabled } = useAllFeatureFlags("ADMIN", "GITLAB_WORKSPACE_CREATION");
 * ```
 */
export function useAllFeatureFlags(...flags: FeatureFlagName[]) {
	const { data, isLoading } = useFeatureFlagsQuery();

	return {
		enabled: data !== undefined && flags.every((f) => data[f] === true),
		isLoading,
	};
}

/**
 * Returns whether ANY of the specified flags are enabled (OR composition).
 *
 * @example
 * ```tsx
 * const { enabled } = useAnyFeatureFlags("ADMIN", "MENTOR_ACCESS");
 * ```
 */
export function useAnyFeatureFlags(...flags: FeatureFlagName[]) {
	const { data, isLoading } = useFeatureFlagsQuery();

	return {
		enabled: data !== undefined && flags.some((f) => data[f] === true),
		isLoading,
	};
}
