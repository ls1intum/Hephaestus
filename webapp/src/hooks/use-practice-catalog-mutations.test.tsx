import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { assert, beforeEach, describe, expect, it, vi } from "vitest";
import {
	deleteAreaMutation,
	deletePracticeMutation,
	getPracticeQueryKey,
	listAreasQueryKey,
	listPracticesQueryKey,
	placePracticeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Practice } from "@/api/types.gen";
import {
	mockAreas,
	mockPractice,
	mockPractices,
} from "@/components/admin/practices/story-mock-data";
import { usePracticeCatalogMutations } from "./use-practice-catalog-mutations";

vi.mock("@/api/@tanstack/react-query.gen", async (importOriginal) => {
	const actual = await importOriginal<typeof import("@/api/@tanstack/react-query.gen")>();
	return {
		...actual,
		deleteAreaMutation: vi.fn(),
		deletePracticeMutation: vi.fn(),
		placePracticeMutation: vi.fn(),
	};
});

const WORKSPACE = "acme";
const queryKey = listPracticesQueryKey({ path: { workspaceSlug: WORKSPACE } });
const areasQueryKey = listAreasQueryKey({ path: { workspaceSlug: WORKSPACE } });

/** A second optimistic write landing on one row while a placement is still in flight. */
function deactivating(slug: string) {
	return (current: Practice[] = []) =>
		current.map((item) => (item.slug === slug ? { ...item, active: false } : item));
}

function practice(slug: string, areaSlug: string | undefined, displayOrder: number): Practice {
	return {
		...mockPractice,
		id: displayOrder + 1,
		name: slug,
		slug,
		areaSlug,
		displayOrder,
	};
}

function wrapper(client: QueryClient) {
	return function QueryWrapper({ children }: { children: ReactNode }) {
		return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
	};
}

function queryClient() {
	return new QueryClient({
		defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
	});
}

describe("usePracticeCatalogMutations", () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it("optimistically places a practice in another bucket and updates its detail cache", async () => {
		const client = queryClient();
		const first = practice("first", "quality", 0);
		const moving = practice("moving", "quality", 1);
		const last = practice("last", "quality", 2);
		const destination = practice("destination", "delivery", 0);
		client.setQueryData(queryKey, [first, moving, last, destination]);
		client.setQueryData(
			getPracticeQueryKey({
				path: { workspaceSlug: WORKSPACE, practiceSlug: "moving" },
			}),
			moving,
		);
		let resolveRequest: (value: Practice[]) => void = () => {};
		vi.mocked(placePracticeMutation).mockReturnValue({
			mutationFn: () =>
				new Promise<Practice[]>((resolve) => {
					resolveRequest = resolve;
				}),
		});
		const { result } = renderHook(() => usePracticeCatalogMutations(WORKSPACE), {
			wrapper: wrapper(client),
		});

		act(() => {
			result.current.placePractice.mutate({
				path: { workspaceSlug: WORKSPACE, practiceSlug: "moving" },
				body: { areaSlug: "delivery", position: 0 },
			});
		});

		await waitFor(() =>
			expect(
				client.getQueryData<Practice[]>(queryKey)?.find(({ slug }) => slug === "moving"),
			).toMatchObject({ areaSlug: "delivery", displayOrder: 0 }),
		);
		expect(
			client.getQueryData<Practice>(
				getPracticeQueryKey({
					path: { workspaceSlug: WORKSPACE, practiceSlug: "moving" },
				}),
			),
		).toMatchObject({ areaSlug: "delivery", displayOrder: 0 });
		expect(
			client
				.getQueryData<Practice[]>(queryKey)
				?.filter(({ areaSlug }) => areaSlug === "quality")
				.map(({ displayOrder }) => displayOrder),
		).toStrictEqual([0, 1]);

		resolveRequest([
			first,
			{ ...last, displayOrder: 1 },
			{ ...moving, areaSlug: "delivery", displayOrder: 0 },
			{ ...destination, displayOrder: 1 },
		]);
		await waitFor(() => expect(result.current.placePractice.isSuccess).toBe(true));
	});

	it("rolls back placement fields without overwriting another optimistic field", async () => {
		const client = queryClient();
		const practices = [practice("moving", "quality", 0), practice("remaining", "quality", 1)];
		client.setQueryData(queryKey, practices);
		let rejectRequest: (reason: Error) => void = () => {};
		vi.mocked(placePracticeMutation).mockReturnValue({
			mutationFn: () =>
				new Promise<Practice[]>((_resolve, reject) => {
					rejectRequest = reject;
				}),
		});
		const { result } = renderHook(() => usePracticeCatalogMutations(WORKSPACE), {
			wrapper: wrapper(client),
		});

		act(() => {
			result.current.placePractice.mutate({
				path: { workspaceSlug: WORKSPACE, practiceSlug: "moving" },
				body: { position: 0 },
			});
		});
		await waitFor(() => {
			const cached = client.getQueryData<Practice[]>(queryKey);
			assert(cached);
			const [moved] = cached;
			assert(moved);
			expect(moved.areaSlug).toBeUndefined();
		});
		client.setQueryData<Practice[]>(queryKey, deactivating("moving"));
		rejectRequest(new Error("rejected"));

		await waitFor(() => expect(result.current.placePractice.isError).toBe(true));
		expect(
			client.getQueryData<Practice[]>(queryKey)?.find(({ slug }) => slug === "moving"),
		).toMatchObject({
			active: false,
			areaSlug: "quality",
			displayOrder: 0,
		});
	});

	it("blocks structural changes while a placement settles", async () => {
		const client = queryClient();
		client.setQueryData(queryKey, [
			practice("first", "quality", 0),
			practice("second", "quality", 1),
		]);
		let resolveFirst: (value: Practice[]) => void = () => {};
		const mutation = vi.fn(
			() =>
				new Promise<Practice[]>((resolve) => {
					resolveFirst = resolve;
				}),
		);
		vi.mocked(placePracticeMutation).mockReturnValue({ mutationFn: mutation });
		const { result } = renderHook(() => usePracticeCatalogMutations(WORKSPACE), {
			wrapper: wrapper(client),
		});

		act(() => {
			result.current.placePractice.mutate({
				path: { workspaceSlug: WORKSPACE, practiceSlug: "first" },
				body: { areaSlug: "quality", position: 1 },
			});
		});

		await waitFor(() => expect(result.current.placePractice.isPending).toBe(true));
		expect(result.current.areaStructurePending).toBe(true);
		expect(result.current.blockedPracticeOrderBuckets).toStrictEqual(
			new Set(["__unassigned__", "quality"]),
		);
		expect(result.current.blockedMoveDestinationSlugs).toStrictEqual(
			new Set(["__unassigned__", "quality"]),
		);
		resolveFirst([practice("second", "quality", 0), practice("first", "quality", 1)]);
		await waitFor(() => expect(result.current.placePractice.isSuccess).toBe(true));
	});

	it("blocks reordering only in the bucket being deleted from", async () => {
		const client = queryClient();
		client.setQueryData(queryKey, [
			practice("deleting", "quality", 0),
			practice("elsewhere", "delivery", 0),
		]);
		let resolveDelete: () => void = () => {};
		vi.mocked(deletePracticeMutation).mockReturnValue({
			mutationFn: () =>
				new Promise<void>((resolve) => {
					resolveDelete = resolve;
				}),
		});
		const { result } = renderHook(() => usePracticeCatalogMutations(WORKSPACE), {
			wrapper: wrapper(client),
		});

		act(() => {
			result.current.deletePractice.mutate({
				path: { workspaceSlug: WORKSPACE, practiceSlug: "deleting" },
			});
		});

		await waitFor(() => expect(result.current.deletePractice.isPending).toBe(true));
		expect(result.current.blockedPracticeOrderBuckets).toStrictEqual(new Set(["quality"]));
		expect(result.current.blockedMoveDestinationSlugs).toStrictEqual(new Set(["quality"]));
		resolveDelete();
		await waitFor(() => expect(result.current.deletePractice.isSuccess).toBe(true));
	});

	it("removes a deleting area from move destinations", async () => {
		const client = queryClient();
		client.setQueryData(areasQueryKey, mockAreas);
		client.setQueryData(queryKey, mockPractices);
		let resolveDelete: () => void = () => {};
		vi.mocked(deleteAreaMutation).mockReturnValue({
			mutationFn: () =>
				new Promise<void>((resolve) => {
					resolveDelete = resolve;
				}),
		});
		const { result } = renderHook(() => usePracticeCatalogMutations(WORKSPACE), {
			wrapper: wrapper(client),
		});

		act(() => {
			result.current.deleteArea.mutate({
				path: { workspaceSlug: WORKSPACE, areaSlug: "quality" },
			});
		});

		await waitFor(() => expect(result.current.deleteArea.isPending).toBe(true));
		expect(result.current.blockedMoveDestinationSlugs).toStrictEqual(
			new Set(["quality", "__unassigned__"]),
		);
		resolveDelete();
		await waitFor(() => expect(result.current.deleteArea.isSuccess).toBe(true));
	});
});
