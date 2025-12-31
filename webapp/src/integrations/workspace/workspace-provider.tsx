import { useQuery } from "@tanstack/react-query";
import { createContext, type ReactNode, useContext, useEffect } from "react";
import {
	getCurrentUserMembershipOptions,
	getWorkspaceOptions,
} from "@/api/@tanstack/react-query.gen";
import type { Workspace, WorkspaceMembership } from "@/api/types.gen";
import { useAuth } from "@/integrations/auth/AuthContext";

/** LocalStorage key for persisting last visited workspace slug */
const LAST_WORKSPACE_SLUG_KEY = "hephaestus.lastWorkspaceSlug";

export type WorkspaceErrorType = "not-found" | "forbidden" | "error" | null;

export interface WorkspaceContextType {
	/** Current workspace data (null during loading or on error) */
	workspace: Workspace | null;
	/** Current user's membership in the workspace (null if not a member or loading) */
	membership: WorkspaceMembership | null;
	/** The workspace slug being fetched */
	slug: string;
	/** True while workspace or membership data is loading */
	isLoading: boolean;
	/** Error type for rendering appropriate error states */
	errorType: WorkspaceErrorType;
	/** Raw error object for debugging/logging */
	error: Error | null;
	/** True if workspace data was successfully loaded */
	isReady: boolean;
	/** The user's role in the workspace (convenience accessor) */
	role: WorkspaceMembership["role"] | undefined;
	/** True if user has ADMIN or OWNER role */
	isAdmin: boolean;
	/** True if user has OWNER role */
	isOwner: boolean;
	/** True if user is a member of this workspace */
	isMember: boolean;
}

const WorkspaceContext = createContext<WorkspaceContextType | undefined>(undefined);

/**
 * Exported for use by MockWorkspaceProvider in tests.
 * @internal
 */
export { WorkspaceContext };

export interface WorkspaceProviderProps {
	/** Workspace slug to fetch */
	slug: string;
	/** Child components to render */
	children: ReactNode;
}

/**
 * Type guard to check if a value is an Error instance.
 */
function isError(value: unknown): value is Error {
	return value instanceof Error;
}

/**
 * Type guard to check if a value is an object with a status property.
 */
function hasStatusCode(value: unknown): value is { status: number } {
	return typeof value === "object" && value !== null && "status" in value;
}

/**
 * Determines the error type from an HTTP error response.
 */
function getErrorType(error: unknown): WorkspaceErrorType {
	if (!error) return null;

	// Check for fetch/HTTP errors with status codes (most reliable)
	if (hasStatusCode(error)) {
		if (error.status === 404) return "not-found";
		if (error.status === 403) return "forbidden";
		// 401 treated as forbidden - user needs to authenticate
		if (error.status === 401) return "forbidden";
	}

	return "error";
}

/**
 * Custom retry function that skips retries for expected error states (403/404/401).
 * These are not transient errors and retrying won't help.
 */
function shouldRetry(failureCount: number, error: unknown): boolean {
	const errorType = getErrorType(error);
	if (errorType === "not-found" || errorType === "forbidden") return false;
	return failureCount < 2;
}

/**
 * Persists the last visited workspace slug to localStorage.
 */
function persistLastWorkspaceSlug(slug: string): void {
	try {
		localStorage.setItem(LAST_WORKSPACE_SLUG_KEY, slug);
	} catch {
		// Ignore localStorage errors (e.g., in incognito mode)
	}
}

/**
 * Retrieves the last visited workspace slug from localStorage.
 */
export function getLastWorkspaceSlug(): string | null {
	try {
		return localStorage.getItem(LAST_WORKSPACE_SLUG_KEY);
	} catch {
		return null;
	}
}

/**
 * Clears the persisted workspace slug from localStorage.
 */
export function clearLastWorkspaceSlug(): void {
	try {
		localStorage.removeItem(LAST_WORKSPACE_SLUG_KEY);
	} catch {
		// Ignore localStorage errors
	}
}

/**
 * Provider component that fetches and caches workspace data by slug.
 * Uses TanStack Query for deduplication and caching.
 *
 * @example
 * ```tsx
 * <WorkspaceProvider slug={params.workspaceSlug}>
 *   <WorkspaceDashboard />
 * </WorkspaceProvider>
 * ```
 */
export function WorkspaceProvider({ slug, children }: WorkspaceProviderProps) {
	const { isAuthenticated, isLoading: authLoading } = useAuth();
	const isEnabled = Boolean(slug) && isAuthenticated && !authLoading;

	// Fetch workspace data
	const workspaceQuery = useQuery({
		...getWorkspaceOptions({
			path: { workspaceSlug: slug },
		}),
		enabled: isEnabled,
		retry: shouldRetry,
	});

	// Fetch membership data
	const membershipQuery = useQuery({
		...getCurrentUserMembershipOptions({
			path: { workspaceSlug: slug },
		}),
		enabled: isEnabled,
		retry: shouldRetry,
	});

	// Persist slug to localStorage when workspace loads successfully
	useEffect(() => {
		if (workspaceQuery.data && slug) {
			persistLastWorkspaceSlug(slug);
		}
	}, [workspaceQuery.data, slug]);

	// Clear persisted slug when user logs out
	useEffect(() => {
		if (!isAuthenticated && !authLoading) {
			clearLastWorkspaceSlug();
		}
	}, [isAuthenticated, authLoading]);

	// Derive loading state
	const isLoading =
		authLoading || (isEnabled && (workspaceQuery.isLoading || membershipQuery.isLoading));

	// Determine error type (prioritize workspace errors)
	const error = workspaceQuery.error ?? membershipQuery.error ?? null;
	const errorType = getErrorType(error);

	// Derive membership-based flags
	const role = membershipQuery.data?.role;
	const isMember = Boolean(role);
	const isAdmin = role === "ADMIN" || role === "OWNER";
	const isOwner = role === "OWNER";

	// Normalize error for consumer convenience - extract Error if available, otherwise wrap
	const normalizedError: Error | null = isError(error)
		? error
		: error
			? new Error(String(error))
			: null;

	const value: WorkspaceContextType = {
		workspace: workspaceQuery.data ?? null,
		membership: membershipQuery.data ?? null,
		slug,
		isLoading,
		errorType,
		error: normalizedError,
		isReady: Boolean(workspaceQuery.data) && !isLoading && !errorType,
		role,
		isAdmin,
		isOwner,
		isMember,
	};

	return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

/**
 * Hook to access workspace context.
 * Must be used within a WorkspaceProvider.
 *
 * @throws Error if used outside of WorkspaceProvider
 *
 * @example
 * ```tsx
 * function WorkspaceDashboard() {
 *   const { workspace, isLoading, errorType, isAdmin } = useWorkspace();
 *
 *   if (isLoading) return <LoadingSpinner />;
 *   if (errorType === 'not-found') return <WorkspaceNotFound />;
 *   if (errorType === 'forbidden') return <WorkspaceForbidden />;
 *
 *   return <div>{workspace?.displayName}</div>;
 * }
 * ```
 */
export function useWorkspace(): WorkspaceContextType {
	const context = useContext(WorkspaceContext);
	if (!context) {
		throw new Error("useWorkspace must be used within a WorkspaceProvider");
	}
	return context;
}

/**
 * Optional hook that returns undefined if used outside of WorkspaceProvider.
 * Useful for components that may or may not be within a workspace context.
 */
export function useWorkspaceOptional(): WorkspaceContextType | undefined {
	return useContext(WorkspaceContext);
}
