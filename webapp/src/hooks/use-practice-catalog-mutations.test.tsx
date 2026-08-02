import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
	deleteAreaMutation,
	deletePracticeMutation,
	getPracticeQueryKey,
	listAreasQueryKey,
	listPracticesQueryKey,
	placePracticeMutation,
} from "@/api/@tanstack/react-query.gen";
import type { Practice } from "@/api/types.gen";
import { mockAreas, mockPractices } from "@/components/admin/practices/story-mock-data";
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

function practice(slug: string, areaSlug: string | undefined, displayOrder: number): Practice {
	return {
		...mockPractices[0],
		id: displayOrder + 1,
		name: slug,
		slug,
		areaSlug,
		displayOrder,
	};
}

function wrapper(queryClient: QueryClient) {
	return function QueryWrapper({ children }: { children: ReactNode }) {
		return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
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
		const practices = [
			practice("first", "quality", 0),
			practice("moving", "quality", 1),
			practice("last", "quality", 2),
			practice("destination", "delivery", 0),
		];
		client.setQueryData(queryKey, practices);
		client.setQueryData(
			getPracticeQueryKey({
				path: { workspaceSlug: WORKSPACE, practiceSlug: "moving" },
			}),
			practices[1],
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
		).toEqual([0, 1]);

		resolveRequest([
			practices[0],
			{ ...practices[2], displayOrder: 1 },
			{ ...practices[1], areaSlug: "delivery", displayOrder: 0 },
			{ ...practices[3], displayOrder: 1 },
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
		await waitFor(() =>
			expect(client.getQueryData<Practice[]>(queryKey)?.[0].areaSlug).toBeUndefined(),
		);
		client.setQueryData<Practice[]>(queryKey, (current = []) =>
			current.map((item) => (item.slug === "moving" ? { ...item, active: false } : item)),
		);
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
		expect(result.current.blockedPracticeOrderBuckets).toEqual(
			new Set(["__unassigned__", "quality"]),
		);
		expect(result.current.blockedMoveDestinationSlugs).toEqual(
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
		expect(result.current.blockedPracticeOrderBuckets).toEqual(new Set(["quality"]));
		expect(result.current.blockedMoveDestinationSlugs).toEqual(new Set(["quality"]));
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
		expect(result.current.blockedMoveDestinationSlugs).toEqual(
			new Set(["quality", "__unassigned__"]),
		);
		resolveDelete();
		await waitFor(() => expect(result.current.deleteArea.isSuccess).toBe(true));
	});
});
