import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { assert, beforeEach, describe, expect, it, vi } from "vitest";

import {
	deletePracticeGroupMutation,
	deletePracticeMutation,
	getPracticeQueryKey,
	listAdoptablePracticesQueryKey,
	listPracticeGroupsQueryKey,
	listPracticesQueryKey,
	movePracticeMutation,
	previewGroupAdoptionQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type * as ReactQueryGen from "@/api/@tanstack/react-query.gen";
import type { Practice } from "@/api/types.gen";
import {
	chosenFeedbackMode,
	mockGroups,
	mockPractice,
} from "@/components/admin/practices/fixtures";
import { deferred } from "@/test/async";

import { usePracticeCatalogMutations } from "./use-practice-catalog-mutations";

vi.mock("@/api/@tanstack/react-query.gen", async (importOriginal) => {
	const actual = await importOriginal<typeof ReactQueryGen>();
	return {
		...actual,
		deletePracticeGroupMutation: vi.fn(),
		deletePracticeMutation: vi.fn(),
		movePracticeMutation: vi.fn(),
	};
});

const WORKSPACE = "acme";
const queryKey = listPracticesQueryKey({ path: { workspaceSlug: WORKSPACE } });
const groupsQueryKey = listPracticeGroupsQueryKey({ path: { workspaceSlug: WORKSPACE } });
const adoptionCatalogQueryKey = listAdoptablePracticesQueryKey({
	path: { workspaceSlug: WORKSPACE },
});

function switchingOff(slug: string) {
	return (current: Practice[] = []) =>
		current.map((item) =>
			item.slug === slug ? { ...item, feedbackMode: chosenFeedbackMode("OFF") } : item,
		);
}

function practice(slug: string, groupSlug: string | null, position: number): Practice {
	return { ...mockPractice, id: String(position + 1), slug, groupSlug, position };
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

	it("optimistically places a practice in another group and updates its detail cache", async () => {
		const client = queryClient();
		const first = practice("first", "quality", 0);
		const moving = practice("moving", "quality", 1);
		const last = practice("last", "quality", 2);
		const destination = practice("destination", "delivery", 0);
		client.setQueryData(queryKey, [first, moving, last, destination]);
		const detailKey = getPracticeQueryKey({
			path: { workspaceSlug: WORKSPACE, practiceSlug: "moving" },
		});
		client.setQueryData(detailKey, moving);
		const request = deferred<Practice[]>();
		vi.mocked(movePracticeMutation).mockReturnValue({
			mutationFn: async () => request.promise,
		});
		const { result } = renderHook(() => usePracticeCatalogMutations(WORKSPACE), {
			wrapper: wrapper(client),
		});

		act(() => {
			result.current.movePractice.mutate({
				path: { workspaceSlug: WORKSPACE, practiceSlug: "moving" },
				body: { groupSlug: "delivery", position: 0 },
			});
		});

		await waitFor(() =>
			expect(
				client.getQueryData<Practice[]>(queryKey)?.find(({ slug }) => slug === "moving"),
			).toMatchObject({ groupSlug: "delivery", position: 0 }),
		);
		expect(client.getQueryData<Practice>(detailKey)).toMatchObject({
			groupSlug: "delivery",
			position: 0,
		});
		expect(
			client
				.getQueryData<Practice[]>(queryKey)
				?.filter(({ groupSlug }) => groupSlug === "quality")
				.map(({ position }) => position),
		).toStrictEqual([0, 1]);

		request.resolve([
			first,
			{ ...last, position: 1 },
			{ ...moving, groupSlug: "delivery", position: 0 },
			{ ...destination, position: 1 },
		]);
		await waitFor(() => expect(result.current.movePractice.isSuccess).toBe(true));
		expect(
			client.getQueryData<Practice[]>(queryKey)?.find(({ slug }) => slug === "destination")
				?.position,
		).toBe(1);
	});

	it("rolls back the moved positions without overwriting another optimistic field", async () => {
		const client = queryClient();
		const practices = [practice("moving", "quality", 0), practice("remaining", "quality", 1)];
		client.setQueryData(queryKey, practices);
		const request = deferred<Practice[]>();
		vi.mocked(movePracticeMutation).mockReturnValue({
			mutationFn: async () => request.promise,
		});
		const { result } = renderHook(() => usePracticeCatalogMutations(WORKSPACE), {
			wrapper: wrapper(client),
		});

		act(() => {
			result.current.movePractice.mutate({
				path: { workspaceSlug: WORKSPACE, practiceSlug: "moving" },
				body: { groupSlug: null, position: 0 },
			});
		});
		await waitFor(() => {
			const cached = client.getQueryData<Practice[]>(queryKey);
			assert(cached);
			const [moved] = cached;
			assert(moved);
			expect(moved.groupSlug).toBeNull();
		});
		client.setQueryData<Practice[]>(queryKey, switchingOff("moving"));
		request.reject(new Error("rejected"));

		await waitFor(() => expect(result.current.movePractice.isError).toBe(true));
		expect(
			client.getQueryData<Practice[]>(queryKey)?.find(({ slug }) => slug === "moving"),
		).toMatchObject({
			feedbackMode: chosenFeedbackMode("OFF"),
			groupSlug: "quality",
			position: 0,
		});
	});

	it("blocks structural changes while a move settles", async () => {
		const client = queryClient();
		client.setQueryData(queryKey, [
			practice("first", "quality", 0),
			practice("second", "quality", 1),
		]);
		const first = deferred<Practice[]>();
		const mutation = vi.fn(async () => first.promise);
		vi.mocked(movePracticeMutation).mockReturnValue({ mutationFn: mutation });
		const { result } = renderHook(() => usePracticeCatalogMutations(WORKSPACE), {
			wrapper: wrapper(client),
		});

		act(() => {
			result.current.movePractice.mutate({
				path: { workspaceSlug: WORKSPACE, practiceSlug: "first" },
				body: { groupSlug: "quality", position: 1 },
			});
		});

		await waitFor(() => expect(result.current.movePractice.isPending).toBe(true));
		expect(result.current.groupStructurePending).toBe(true);
		expect(result.current.blockedPracticeOrderBuckets).toStrictEqual(
			new Set(["__unassigned__", "quality"]),
		);
		expect(result.current.blockedMoveDestinationSlugs).toStrictEqual(
			new Set(["__unassigned__", "quality"]),
		);
		first.resolve([practice("second", "quality", 0), practice("first", "quality", 1)]);
		await waitFor(() => expect(result.current.movePractice.isSuccess).toBe(true));
	});

	it("blocks reordering only in the bucket being deleted from", async () => {
		const client = queryClient();
		client.setQueryData(queryKey, [
			practice("deleting", "quality", 0),
			practice("elsewhere", "delivery", 0),
		]);
		client.setQueryData(adoptionCatalogQueryKey, []);
		const deletion = deferred();
		vi.mocked(deletePracticeMutation).mockReturnValue({
			mutationFn: async () => deletion.promise,
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
		deletion.resolve();
		await waitFor(() => expect(result.current.deletePractice.isSuccess).toBe(true));
		expect(client.getQueryState(adoptionCatalogQueryKey)?.isInvalidated).toBe(true);
	});

	it("removes a deleting group from move destinations", async () => {
		const client = queryClient();
		client.setQueryData(groupsQueryKey, mockGroups);
		const affected = practice("ungrouped-by-delete", "quality", 0);
		client.setQueryData(queryKey, [affected]);
		const affectedDetailKey = getPracticeQueryKey({
			path: { workspaceSlug: WORKSPACE, practiceSlug: affected.slug },
		});
		client.setQueryData(affectedDetailKey, affected);
		const groupPreviewKey = previewGroupAdoptionQueryKey({
			path: { workspaceSlug: WORKSPACE, slug: "quality" },
		});
		client.setQueryData(groupPreviewKey, {});
		const deletion = deferred();
		vi.mocked(deletePracticeGroupMutation).mockReturnValue({
			mutationFn: async () => deletion.promise,
		});
		const { result } = renderHook(() => usePracticeCatalogMutations(WORKSPACE), {
			wrapper: wrapper(client),
		});

		act(() => {
			result.current.deleteGroup.mutate({
				path: { workspaceSlug: WORKSPACE, groupSlug: "quality" },
			});
		});

		await waitFor(() => expect(result.current.deleteGroup.isPending).toBe(true));
		expect(result.current.blockedMoveDestinationSlugs).toStrictEqual(
			new Set(["quality", "__unassigned__"]),
		);
		deletion.resolve();
		await waitFor(() => expect(result.current.deleteGroup.isSuccess).toBe(true));
		expect(client.getQueryState(groupPreviewKey)?.isInvalidated).toBe(true);
		expect(client.getQueryState(affectedDetailKey)?.isInvalidated).toBe(true);
	});

	it("replaces an in-flight first detail read after its group is deleted", async () => {
		const client = queryClient();
		const before = practice("moving", "quality", 0);
		const after = { ...before, groupSlug: null, position: 4 };
		client.setQueryData(queryKey, [before]);
		const firstRead = deferred<Practice>();
		const read = vi
			.fn()
			.mockImplementationOnce(async () => firstRead.promise)
			.mockResolvedValue(after);
		const detailKey = getPracticeQueryKey({
			path: { workspaceSlug: WORKSPACE, practiceSlug: before.slug },
		});
		vi.mocked(deletePracticeGroupMutation).mockReturnValue({
			mutationFn: vi.fn().mockResolvedValue(undefined),
		});
		const { result, unmount } = renderHook(
			() => {
				const detail = useQuery({ queryKey: detailKey, queryFn: read });
				const mutations = usePracticeCatalogMutations(WORKSPACE);
				return { detail, mutations };
			},
			{ wrapper: wrapper(client) },
		);
		await waitFor(() => expect(read).toHaveBeenCalledOnce());
		expect(result.current.detail.data).toBeUndefined();
		act(() =>
			result.current.mutations.deleteGroup.mutate({
				path: { workspaceSlug: WORKSPACE, groupSlug: "quality" },
			}),
		);
		await waitFor(() => expect(result.current.detail.data).toStrictEqual(after));
		await waitFor(() => expect(result.current.mutations.deleteGroup.isSuccess).toBe(true));
		firstRead.resolve(before);
		await act(async () => {
			await firstRead.promise;
		});
		expect(client.getQueryData(detailKey)).toStrictEqual(after);
		unmount();
		client.clear();
	});
});
