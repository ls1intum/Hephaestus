import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	getConnectionSyncStatusQueryKey,
	listConnectionSyncJobsQueryKey,
	listConnectionSyncResourcesQueryKey,
	listOutlineCollectionsQueryKey,
	listSlackChannelsQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { useSyncEvents } from "./useSyncEvents";

const WORKSPACE = "test-workspace";
const CONNECTION_ID = 42;

class FakeEventSource {
	static readonly CLOSED = 2;
	static instances: FakeEventSource[] = [];

	readyState = 1;
	onopen: (() => void) | null = null;
	onerror: (() => void) | null = null;
	closed = false;
	private syncListener?: (event: MessageEvent<string>) => void;

	constructor(
		readonly url: string,
		readonly options?: EventSourceInit,
	) {
		FakeEventSource.instances.push(this);
	}

	addEventListener(type: string, listener: EventListenerOrEventListenerObject) {
		if (type === "sync") this.syncListener = listener as (event: MessageEvent<string>) => void;
	}

	removeEventListener(type: string) {
		if (type === "sync") this.syncListener = undefined;
	}

	close() {
		this.closed = true;
	}

	emit(scope: string, connectionId = CONNECTION_ID) {
		this.syncListener?.({ data: JSON.stringify({ scope, connectionId }) } as MessageEvent<string>);
	}
}

function wrapper(queryClient: QueryClient) {
	return ({ children }: { children: ReactNode }) => (
		<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
	);
}

describe("useSyncEvents", () => {
	beforeEach(() => {
		FakeEventSource.instances = [];
		vi.stubGlobal("EventSource", FakeEventSource);
	});

	afterEach(() => vi.unstubAllGlobals());

	it("opens a credentialed workspace stream and refreshes authoritative state on reconnect", () => {
		const queryClient = new QueryClient();
		const invalidate = vi.spyOn(queryClient, "invalidateQueries");
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(queryClient) });
		const source = FakeEventSource.instances[0];

		act(() => source.onopen?.());

		expect(source.url).toBe(`http://localhost:8080/workspaces/${WORKSPACE}/sync/events`);
		expect(source.options).toEqual({ withCredentials: true });
		const filter = invalidate.mock.calls[0]?.[0]?.predicate;
		expect(filter).toBeTypeOf("function");
		const currentKey = listConnectionSyncJobsQueryKey({
			path: { workspaceSlug: WORKSPACE, connectionId: CONNECTION_ID },
		});
		const otherKey = listConnectionSyncJobsQueryKey({
			path: { workspaceSlug: "another-workspace", connectionId: CONNECTION_ID },
		});
		queryClient.setQueryData(currentKey, []);
		queryClient.setQueryData(otherKey, []);
		const currentQuery = queryClient.getQueryCache().find({ queryKey: currentKey, exact: true });
		const otherQuery = queryClient.getQueryCache().find({ queryKey: otherKey, exact: true });
		if (!filter || !currentQuery || !otherQuery)
			throw new Error("Expected cached queries and filter");
		expect(filter(currentQuery)).toBe(true);
		expect(filter(otherQuery)).toBe(false);
	});

	it("maps job and resource hints to every affected cache", () => {
		const queryClient = new QueryClient();
		const invalidate = vi.spyOn(queryClient, "invalidateQueries");
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(queryClient) });
		const source = FakeEventSource.instances[0];

		act(() => source.emit("job"));
		expect(invalidate).toHaveBeenCalledWith({
			queryKey: listConnectionSyncJobsQueryKey({
				path: { workspaceSlug: WORKSPACE, connectionId: CONNECTION_ID },
			}),
		});

		invalidate.mockClear();
		act(() => source.emit("resources"));
		expect(invalidate).toHaveBeenCalledWith({
			queryKey: listConnectionSyncResourcesQueryKey({
				path: { workspaceSlug: WORKSPACE, connectionId: CONNECTION_ID },
			}),
		});
		expect(invalidate).toHaveBeenCalledWith({
			queryKey: getConnectionSyncStatusQueryKey({
				path: { workspaceSlug: WORKSPACE, connectionId: CONNECTION_ID },
			}),
		});
		expect(invalidate).toHaveBeenCalledWith({
			queryKey: listOutlineCollectionsQueryKey({ path: { workspaceSlug: WORKSPACE } }),
		});
		expect(invalidate).toHaveBeenCalledWith({
			queryKey: listSlackChannelsQueryKey({ path: { workspaceSlug: WORKSPACE } }),
		});
	});

	it("reports permanent failure and closes the stream on unmount", () => {
		const queryClient = new QueryClient();
		const { result, unmount } = renderHook(() => useSyncEvents(WORKSPACE), {
			wrapper: wrapper(queryClient),
		});
		const source = FakeEventSource.instances[0];
		source.readyState = FakeEventSource.CLOSED;

		act(() => source.onerror?.());
		expect(result.current).toBe(true);

		unmount();
		expect(source.closed).toBe(true);
	});
});
