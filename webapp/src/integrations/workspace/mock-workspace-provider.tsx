import type { ReactNode } from "react";
import type { Workspace, WorkspaceMembership } from "@/api/types.gen";
import {
	WorkspaceContext,
	type WorkspaceContextType,
	type WorkspaceErrorType,
} from "./workspace-provider";

/** Fixed date constant for deterministic test data */
const MOCK_DATE = new Date("2024-01-01T00:00:00Z");

/**
 * Default mock workspace data for testing.
 */
export const mockWorkspace: Workspace = {
	id: 1,
	workspaceSlug: "test-workspace",
	displayName: "Test Workspace",
	accountLogin: "test-org",
	status: "ACTIVE",
	isPubliclyViewable: false,
	hasSlackToken: false,
	hasSlackSigningSecret: false,
	createdAt: MOCK_DATE,
	updatedAt: MOCK_DATE,
};

/**
 * Default mock membership data for testing.
 */
export const mockMembership: WorkspaceMembership = {
	userId: 1,
	userLogin: "testuser",
	userName: "Test User",
	role: "MEMBER",
	createdAt: MOCK_DATE,
};

/**
 * Default mock context value for testing.
 */
export const defaultMockContext: WorkspaceContextType = {
	workspace: mockWorkspace,
	membership: mockMembership,
	slug: "test-workspace",
	isLoading: false,
	errorType: null,
	error: null,
	isReady: true,
	role: "MEMBER",
	isAdmin: false,
	isOwner: false,
	isMember: true,
};

export interface MockWorkspaceProviderProps {
	/** Override specific context values */
	value?: Partial<WorkspaceContextType>;
	/** Child components to render */
	children: ReactNode;
}

/**
 * Mock provider for testing components that depend on WorkspaceProvider.
 * Uses the same context as the real provider, allowing tested components
 * to use the real useWorkspace hook.
 *
 * @example
 * ```tsx
 * // Default values - components can use the real useWorkspace hook
 * <MockWorkspaceProvider>
 *   <ComponentUnderTest />
 * </MockWorkspaceProvider>
 *
 * // Override specific values
 * <MockWorkspaceProvider value={{ isAdmin: true, role: 'ADMIN' }}>
 *   <AdminComponent />
 * </MockWorkspaceProvider>
 *
 * // Simulate loading state
 * <MockWorkspaceProvider value={{ isLoading: true, isReady: false }}>
 *   <ComponentUnderTest />
 * </MockWorkspaceProvider>
 *
 * // Simulate error state
 * <MockWorkspaceProvider value={{ errorType: 'not-found', workspace: null }}>
 *   <ComponentUnderTest />
 * </MockWorkspaceProvider>
 * ```
 */
export function MockWorkspaceProvider({ value, children }: MockWorkspaceProviderProps) {
	const contextValue: WorkspaceContextType = {
		...defaultMockContext,
		...value,
	};

	return <WorkspaceContext.Provider value={contextValue}>{children}</WorkspaceContext.Provider>;
}

/**
 * Factory function to create mock context with specific overrides.
 * Useful for creating typed test fixtures.
 */
export function createMockWorkspaceContext(
	overrides?: Partial<WorkspaceContextType>,
): WorkspaceContextType {
	return {
		...defaultMockContext,
		...overrides,
	};
}

/**
 * Factory for creating loading state context.
 */
export function createLoadingContext(): WorkspaceContextType {
	return createMockWorkspaceContext({
		workspace: null,
		membership: null,
		isLoading: true,
		isReady: false,
		role: undefined,
		isAdmin: false,
		isOwner: false,
		isMember: false,
	});
}

/**
 * Factory for creating not-found error context.
 */
export function createNotFoundContext(slug = "unknown-workspace"): WorkspaceContextType {
	return createMockWorkspaceContext({
		workspace: null,
		membership: null,
		slug,
		isLoading: false,
		errorType: "not-found" as WorkspaceErrorType,
		error: new Error("Workspace not found"),
		isReady: false,
		role: undefined,
		isAdmin: false,
		isOwner: false,
		isMember: false,
	});
}

/**
 * Factory for creating forbidden error context.
 */
export function createForbiddenContext(slug = "private-workspace"): WorkspaceContextType {
	return createMockWorkspaceContext({
		workspace: null,
		membership: null,
		slug,
		isLoading: false,
		errorType: "forbidden" as WorkspaceErrorType,
		error: new Error("Access forbidden"),
		isReady: false,
		role: undefined,
		isAdmin: false,
		isOwner: false,
		isMember: false,
	});
}

/**
 * Factory for creating admin user context.
 */
export function createAdminContext(): WorkspaceContextType {
	return createMockWorkspaceContext({
		role: "ADMIN",
		isAdmin: true,
		isOwner: false,
		isMember: true,
		membership: {
			...mockMembership,
			role: "ADMIN",
		},
	});
}

/**
 * Factory for creating owner user context.
 */
export function createOwnerContext(): WorkspaceContextType {
	return createMockWorkspaceContext({
		role: "OWNER",
		isAdmin: true,
		isOwner: true,
		isMember: true,
		membership: {
			...mockMembership,
			role: "OWNER",
		},
	});
}
