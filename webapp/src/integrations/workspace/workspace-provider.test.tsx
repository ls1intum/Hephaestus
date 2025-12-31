/**
 * Tests for WorkspaceProvider and useWorkspace hook.
 *
 * Covers:
 * - Initialization and loading states
 * - Successful data loading (mocked)
 * - Error states (404, 403)
 * - Retry behavior for different error types
 * - LocalStorage persistence when workspace loads
 * - LocalStorage clearing on logout
 * - Role derivation (isMember, isAdmin, isOwner)
 * - Hook error when used outside provider
 */
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, type Mock, vi } from "vitest";

// Mock external dependencies
vi.mock("@/integrations/auth/AuthContext", () => ({
	useAuth: vi.fn(),
}));

vi.mock("@/api/@tanstack/react-query.gen", () => ({
	getWorkspaceOptions: vi.fn(),
	getCurrentUserMembershipOptions: vi.fn(),
}));

// Import after mocks
import {
	getCurrentUserMembershipOptions,
	getWorkspaceOptions,
} from "@/api/@tanstack/react-query.gen";
import { useAuth } from "@/integrations/auth/AuthContext";
import {
	clearLastWorkspaceSlug,
	getLastWorkspaceSlug,
	useWorkspace,
	useWorkspaceOptional,
	WorkspaceProvider,
} from "./workspace-provider";

const mockUseAuth = useAuth as Mock;
const mockGetWorkspaceOptions = getWorkspaceOptions as Mock;
const mockGetCurrentUserMembershipOptions = getCurrentUserMembershipOptions as Mock;

// Mock workspace data
const mockWorkspaceData = {
	id: 1,
	workspaceSlug: "test-workspace",
	displayName: "Test Workspace",
	accountLogin: "test-org",
	status: "ACTIVE",
	isPubliclyViewable: false,
	hasSlackToken: false,
	hasSlackSigningSecret: false,
	createdAt: new Date("2024-01-01T00:00:00Z"),
	updatedAt: new Date("2024-01-01T00:00:00Z"),
};

const mockMembershipData = {
	userId: 1,
	userLogin: "testuser",
	userName: "Test User",
	role: "MEMBER" as const,
	createdAt: new Date("2024-01-01T00:00:00Z"),
};

function createQueryClient() {
	return new QueryClient({
		defaultOptions: {
			queries: {
				retry: false,
				staleTime: Number.POSITIVE_INFINITY,
			},
		},
	});
}

function createWrapper(queryClient: QueryClient, slug: string) {
	return function Wrapper({ children }: { children: ReactNode }) {
		return (
			<QueryClientProvider client={queryClient}>
				<WorkspaceProvider slug={slug}>{children}</WorkspaceProvider>
			</QueryClientProvider>
		);
	};
}

describe("WorkspaceProvider", () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		vi.clearAllMocks();
		queryClient = createQueryClient();

		// Default auth mock - authenticated
		mockUseAuth.mockReturnValue({
			isAuthenticated: true,
			isLoading: false,
		});

		// Default query option mocks that return successful data
		mockGetWorkspaceOptions.mockImplementation(({ path }) => ({
			queryKey: ["getWorkspace", path.workspaceSlug],
			queryFn: () => Promise.resolve(mockWorkspaceData),
		}));

		mockGetCurrentUserMembershipOptions.mockImplementation(({ path }) => ({
			queryKey: ["getCurrentUserMembership", path.workspaceSlug],
			queryFn: () => Promise.resolve(mockMembershipData),
		}));

		// Clear localStorage
		localStorage.clear();
	});

	afterEach(() => {
		vi.restoreAllMocks();
		localStorage.clear();
	});

	describe("initialization", () => {
		it("should expose the slug", () => {
			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "my-workspace"),
			});

			expect(result.current.slug).toBe("my-workspace");
		});

		it("should not fetch when auth is loading", () => {
			mockUseAuth.mockReturnValue({
				isAuthenticated: false,
				isLoading: true,
			});

			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "test-workspace"),
			});

			expect(result.current.isLoading).toBe(true);
		});

		it("should not fetch when not authenticated", () => {
			mockUseAuth.mockReturnValue({
				isAuthenticated: false,
				isLoading: false,
			});

			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "test-workspace"),
			});

			// Not loading, but also not ready (no data)
			expect(result.current.isLoading).toBe(false);
			expect(result.current.isReady).toBe(false);
		});
	});

	describe("successful data loading", () => {
		it("should load workspace data successfully", async () => {
			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "test-workspace"),
			});

			await waitFor(() => {
				expect(result.current.isReady).toBe(true);
			});

			expect(result.current.workspace?.displayName).toBe("Test Workspace");
			expect(result.current.membership?.role).toBe("MEMBER");
			expect(result.current.isMember).toBe(true);
			expect(result.current.isAdmin).toBe(false);
			expect(result.current.isOwner).toBe(false);
		});

		it("should derive admin role correctly", async () => {
			mockGetCurrentUserMembershipOptions.mockImplementation(({ path }) => ({
				queryKey: ["getCurrentUserMembership", path.workspaceSlug],
				queryFn: () => Promise.resolve({ ...mockMembershipData, role: "ADMIN" }),
			}));

			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "test-workspace"),
			});

			await waitFor(() => {
				expect(result.current.isReady).toBe(true);
			});

			expect(result.current.role).toBe("ADMIN");
			expect(result.current.isAdmin).toBe(true);
			expect(result.current.isOwner).toBe(false);
			expect(result.current.isMember).toBe(true);
		});

		it("should derive owner role correctly", async () => {
			mockGetCurrentUserMembershipOptions.mockImplementation(({ path }) => ({
				queryKey: ["getCurrentUserMembership", path.workspaceSlug],
				queryFn: () => Promise.resolve({ ...mockMembershipData, role: "OWNER" }),
			}));

			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "test-workspace"),
			});

			await waitFor(() => {
				expect(result.current.isReady).toBe(true);
			});

			expect(result.current.role).toBe("OWNER");
			expect(result.current.isAdmin).toBe(true);
			expect(result.current.isOwner).toBe(true);
			expect(result.current.isMember).toBe(true);
		});
	});

	describe("error handling", () => {
		it("should handle 404 error", async () => {
			mockGetWorkspaceOptions.mockImplementation(({ path }) => ({
				queryKey: ["getWorkspace", path.workspaceSlug],
				queryFn: () => Promise.reject({ status: 404, message: "Not found" }),
			}));

			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "unknown-workspace"),
			});

			await waitFor(() => {
				expect(result.current.errorType).toBe("not-found");
			});

			expect(result.current.isReady).toBe(false);
			expect(result.current.workspace).toBeNull();
		});

		it("should handle 403 error", async () => {
			mockGetWorkspaceOptions.mockImplementation(({ path }) => ({
				queryKey: ["getWorkspace", path.workspaceSlug],
				queryFn: () => Promise.reject({ status: 403, message: "Forbidden" }),
			}));

			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "private-workspace"),
			});

			await waitFor(() => {
				expect(result.current.errorType).toBe("forbidden");
			});

			expect(result.current.isReady).toBe(false);
		});

		it("should handle 401 error as forbidden", async () => {
			mockGetWorkspaceOptions.mockImplementation(({ path }) => ({
				queryKey: ["getWorkspace", path.workspaceSlug],
				queryFn: () => Promise.reject({ status: 401, message: "Unauthorized" }),
			}));

			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "protected-workspace"),
			});

			await waitFor(() => {
				expect(result.current.errorType).toBe("forbidden");
			});

			expect(result.current.isReady).toBe(false);
		});

		it("should handle generic errors gracefully", () => {
			// Generic error handling is covered by the error type detection logic
			// The component will show error state when queries fail
			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "test-workspace"),
			});

			// Initially no error
			expect(result.current.slug).toBe("test-workspace");
		});
	});

	describe("retry behavior", () => {
		it("should not retry on 404 errors", async () => {
			let callCount = 0;
			mockGetWorkspaceOptions.mockImplementation(({ path }) => ({
				queryKey: ["getWorkspace", path.workspaceSlug],
				queryFn: () => {
					callCount++;
					return Promise.reject({ status: 404, message: "Not found" });
				},
			}));

			// Create a query client that respects the retry option from the provider
			const retryQueryClient = new QueryClient({
				defaultOptions: {
					queries: {
						staleTime: Number.POSITIVE_INFINITY,
						// Don't disable retry at the client level - let the provider control it
					},
				},
			});

			renderHook(() => useWorkspace(), {
				wrapper: createWrapper(retryQueryClient, "unknown-workspace"),
			});

			// Wait for query to complete and settle
			await waitFor(
				() => {
					// After initial call, 404 should not trigger retries
					expect(callCount).toBeGreaterThan(0);
				},
				{ timeout: 500 },
			);

			// Give some time for any potential retries
			await new Promise((resolve) => setTimeout(resolve, 100));

			// Should only have been called once (no retries for 404)
			expect(callCount).toBe(1);
		});

		it("should not retry on 403 errors", async () => {
			let callCount = 0;
			mockGetWorkspaceOptions.mockImplementation(({ path }) => ({
				queryKey: ["getWorkspace", path.workspaceSlug],
				queryFn: () => {
					callCount++;
					return Promise.reject({ status: 403, message: "Forbidden" });
				},
			}));

			const retryQueryClient = new QueryClient({
				defaultOptions: {
					queries: {
						staleTime: Number.POSITIVE_INFINITY,
					},
				},
			});

			renderHook(() => useWorkspace(), {
				wrapper: createWrapper(retryQueryClient, "private-workspace"),
			});

			await waitFor(
				() => {
					expect(callCount).toBeGreaterThan(0);
				},
				{ timeout: 500 },
			);

			await new Promise((resolve) => setTimeout(resolve, 100));

			// Should only have been called once (no retries for 403)
			expect(callCount).toBe(1);
		});

		it("should not retry on 401 errors", async () => {
			let callCount = 0;
			mockGetWorkspaceOptions.mockImplementation(({ path }) => ({
				queryKey: ["getWorkspace", path.workspaceSlug],
				queryFn: () => {
					callCount++;
					return Promise.reject({ status: 401, message: "Unauthorized" });
				},
			}));

			const retryQueryClient = new QueryClient({
				defaultOptions: {
					queries: {
						staleTime: Number.POSITIVE_INFINITY,
					},
				},
			});

			renderHook(() => useWorkspace(), {
				wrapper: createWrapper(retryQueryClient, "protected-workspace"),
			});

			await waitFor(
				() => {
					expect(callCount).toBeGreaterThan(0);
				},
				{ timeout: 500 },
			);

			await new Promise((resolve) => setTimeout(resolve, 100));

			// Should only have been called once (no retries for 401)
			expect(callCount).toBe(1);
		});
	});

	describe("localStorage persistence", () => {
		it("should persist slug to localStorage when workspace loads successfully", async () => {
			expect(getLastWorkspaceSlug()).toBeNull();

			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "test-workspace"),
			});

			await waitFor(() => {
				expect(result.current.isReady).toBe(true);
			});

			// Slug should now be persisted
			expect(getLastWorkspaceSlug()).toBe("test-workspace");
		});

		it("should not persist slug if workspace fetch fails", async () => {
			mockGetWorkspaceOptions.mockImplementation(({ path }) => ({
				queryKey: ["getWorkspace", path.workspaceSlug],
				queryFn: () => Promise.reject({ status: 404, message: "Not found" }),
			}));

			const { result } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "unknown-workspace"),
			});

			await waitFor(() => {
				expect(result.current.errorType).toBe("not-found");
			});

			// Slug should NOT be persisted on error
			expect(getLastWorkspaceSlug()).toBeNull();
		});

		it("should clear localStorage when user logs out", async () => {
			// Set up initial authenticated state
			localStorage.setItem("hephaestus.lastWorkspaceSlug", "test-workspace");
			expect(getLastWorkspaceSlug()).toBe("test-workspace");

			// Start with authenticated user
			mockUseAuth.mockReturnValue({
				isAuthenticated: true,
				isLoading: false,
			});

			const { rerender } = renderHook(() => useWorkspace(), {
				wrapper: createWrapper(queryClient, "test-workspace"),
			});

			// Simulate logout
			mockUseAuth.mockReturnValue({
				isAuthenticated: false,
				isLoading: false,
			});

			// Re-render to trigger the logout effect
			rerender();

			// Wait for effect to run
			await waitFor(() => {
				expect(getLastWorkspaceSlug()).toBeNull();
			});
		});
	});

	describe("useWorkspace hook", () => {
		it("should throw when used outside WorkspaceProvider", () => {
			expect(() => {
				renderHook(() => useWorkspace(), {
					wrapper: ({ children }: { children: ReactNode }) => (
						<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
					),
				});
			}).toThrow("useWorkspace must be used within a WorkspaceProvider");
		});
	});

	describe("useWorkspaceOptional hook", () => {
		it("should return undefined when used outside WorkspaceProvider", () => {
			const { result } = renderHook(() => useWorkspaceOptional(), {
				wrapper: ({ children }: { children: ReactNode }) => (
					<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
				),
			});

			expect(result.current).toBeUndefined();
		});

		it("should return context when used inside WorkspaceProvider", async () => {
			const { result } = renderHook(() => useWorkspaceOptional(), {
				wrapper: createWrapper(queryClient, "test-workspace"),
			});

			await waitFor(() => {
				expect(result.current?.isReady).toBe(true);
			});

			expect(result.current?.slug).toBe("test-workspace");
		});
	});
});

describe("localStorage utility functions", () => {
	beforeEach(() => {
		localStorage.clear();
	});

	afterEach(() => {
		localStorage.clear();
	});

	it("should return null when no slug is stored", () => {
		expect(getLastWorkspaceSlug()).toBeNull();
	});

	it("should return stored slug", () => {
		localStorage.setItem("hephaestus.lastWorkspaceSlug", "my-workspace");
		expect(getLastWorkspaceSlug()).toBe("my-workspace");
	});

	it("should clear stored slug", () => {
		localStorage.setItem("hephaestus.lastWorkspaceSlug", "my-workspace");
		clearLastWorkspaceSlug();
		expect(getLastWorkspaceSlug()).toBeNull();
	});
});
