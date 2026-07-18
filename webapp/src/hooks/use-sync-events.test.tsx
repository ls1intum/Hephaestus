import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	getConnectionSyncStatusQueryKey,
	getIntegrationCatalogQueryKey,
	getUserProfileQueryKey,
	listConnectionSyncJobsQueryKey,
	listConnectionSyncResourcesQueryKey,
	listOutlineCollectionsQueryKey,
	listSlackChannelsQueryKey,
} from "@/api/@tanstack/react-query.gen";
import { useSyncEvents } from "./use-sync-events";

const WORKSPACE = "test-workspace";
const CONNECTION_ID = 42;
const HINT_DEBOUNCE_MS = 300;

class FakeEventSource {
	static readonly CONNECTING = 0;
	static readonly OPEN = 1;
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

	/** The spec's "fail the connection" path: any non-200 or wrong Content-Type. No browser retry. */
	fail() {
		this.readyState = FakeEventSource.CLOSED;
		this.onerror?.();
	}
}

function wrapper(queryClient: QueryClient) {
	return ({ children }: { children: ReactNode }) => (
		<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
	);
}

/** Latest source the hook opened — reconnects push a new instance. */
function latestSource(): FakeEventSource {
	const source = FakeEventSource.instances.at(-1);
	if (!source) throw new Error("Expected the hook to have opened a stream");
	return source;
}

/**
 * Let the pending reconnect fire. The ladder is 1s·2ⁿ⁻¹ scaled by 0.5–1.0× jitter, so 5s clears any of the
 * first three attempts — the only depth these tests reach. Fake timers move `Date.now()` too, and
 * that is deliberate here: an early flap really does reconnect seconds later, which is what makes
 * the resync throttle bite.
 */
function runReconnectBackoff() {
	act(() => {
		vi.advanceTimersByTime(5_000);
	});
}

function flushHintDebounce() {
	act(() => {
		vi.advanceTimersByTime(HINT_DEBOUNCE_MS);
	});
}

describe("useSyncEvents", () => {
	beforeEach(() => {
		FakeEventSource.instances = [];
		vi.stubGlobal("EventSource", FakeEventSource);
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.useRealTimers();
		vi.unstubAllGlobals();
	});

	it("opens one credentialed workspace stream", () => {
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(new QueryClient()) });

		expect(FakeEventSource.instances).toHaveLength(1);
		expect(latestSource().url).toBe(`http://localhost:8080/workspaces/${WORKSPACE}/sync/events`);
		expect(latestSource().options).toEqual({ withCredentials: true });
	});

	it("does not resync on the first open, because that would cancel the page's own mount fetches", () => {
		const queryClient = new QueryClient();
		const invalidate = vi.spyOn(queryClient, "invalidateQueries");
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(queryClient) });

		act(() => latestSource().onopen?.());

		expect(invalidate).not.toHaveBeenCalled();
	});

	it("resyncs on re-open, scoped to this workspace's integration queries and nothing else", () => {
		const queryClient = new QueryClient();
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(queryClient) });
		act(() => latestSource().onopen?.());

		const invalidate = vi.spyOn(queryClient, "invalidateQueries");
		act(() => latestSource().fail());
		runReconnectBackoff();
		// A re-open more than the throttle window after the first one must catch up on whatever the
		// stream dropped while it was down.
		vi.setSystemTime(Date.now() + 60_000);
		act(() => latestSource().onopen?.());

		const filter = invalidate.mock.calls[0]?.[0]?.predicate;
		expect(filter).toBeTypeOf("function");
		if (!filter) throw new Error("Expected a scoped predicate");

		const included = listConnectionSyncJobsQueryKey({
			path: { workspaceSlug: WORKSPACE, connectionId: CONNECTION_ID },
		});
		const otherWorkspace = listConnectionSyncJobsQueryKey({
			path: { workspaceSlug: "another-workspace", connectionId: CONNECTION_ID },
		});
		// Not an integration query: a workspace-wide predicate would sweep this in; the scoped predicate must not.
		const unrelated = getUserProfileQueryKey({ path: { workspaceSlug: WORKSPACE, login: "ada" } });
		for (const key of [included, otherWorkspace, unrelated]) queryClient.setQueryData(key, []);

		const find = (key: readonly unknown[]) => {
			const query = queryClient.getQueryCache().find({ queryKey: key, exact: true });
			if (!query) throw new Error("Expected a cached query");
			return query;
		};
		expect(filter(find(included))).toBe(true);
		expect(filter(find(otherWorkspace))).toBe(false);
		expect(filter(find(unrelated))).toBe(false);
	});

	it("throttles catch-up resyncs so a flapping stream cannot storm the cache", () => {
		const queryClient = new QueryClient();
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(queryClient) });
		act(() => latestSource().onopen?.());

		const invalidate = vi.spyOn(queryClient, "invalidateQueries");
		// Two reconnects back to back, both well inside the 30s throttle window.
		for (let i = 0; i < 2; i += 1) {
			act(() => latestSource().fail());
			runReconnectBackoff();
			act(() => latestSource().onopen?.());
		}

		expect(invalidate).not.toHaveBeenCalled();
	});

	it("reconnects itself after a failed connection, which the browser never retries", () => {
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(new QueryClient()) });
		act(() => latestSource().onopen?.());
		const first = latestSource();

		act(() => first.fail());
		expect(first.closed).toBe(true);
		runReconnectBackoff();

		expect(FakeEventSource.instances).toHaveLength(2);
		expect(latestSource()).not.toBe(first);
	});

	it("leaves a CONNECTING stream alone — the browser is already retrying that one", () => {
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(new QueryClient()) });
		const source = latestSource();
		act(() => source.onopen?.());

		source.readyState = FakeEventSource.CONNECTING;
		act(() => source.onerror?.());
		runReconnectBackoff();

		expect(FakeEventSource.instances).toHaveLength(1);
		expect(source.closed).toBe(false);
	});

	it("reports live push as unavailable only once a retry has also failed, and clears it on recovery", () => {
		const { result } = renderHook(() => useSyncEvents(WORKSPACE), {
			wrapper: wrapper(new QueryClient()),
		});
		act(() => latestSource().onopen?.());

		// One blip is not worth telling the admin about — the reconnect usually wins within a second.
		act(() => latestSource().fail());
		expect(result.current).toBe(false);

		runReconnectBackoff();
		act(() => latestSource().fail());
		expect(result.current).toBe(true);

		runReconnectBackoff();
		act(() => latestSource().onopen?.());
		expect(result.current).toBe(false);
	});

	it("maps a job hint to the status and history of that connection", () => {
		const queryClient = new QueryClient();
		const invalidate = vi.spyOn(queryClient, "invalidateQueries");
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(queryClient) });

		act(() => latestSource().emit("job"));
		flushHintDebounce();

		expect(invalidate).toHaveBeenCalledWith({
			queryKey: getConnectionSyncStatusQueryKey({
				path: { workspaceSlug: WORKSPACE, connectionId: CONNECTION_ID },
			}),
		});
		expect(invalidate).toHaveBeenCalledWith({
			queryKey: listConnectionSyncJobsQueryKey({
				path: { workspaceSlug: WORKSPACE, connectionId: CONNECTION_ID },
			}),
		});
	});

	it("collapses a burst of hints from a chatty job into one refetch per scope", () => {
		const queryClient = new QueryClient();
		const invalidate = vi.spyOn(queryClient, "invalidateQueries");
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(queryClient) });

		act(() => {
			for (let i = 0; i < 5; i += 1) latestSource().emit("job");
		});
		flushHintDebounce();

		// One status + one jobs invalidation, not five of each.
		expect(invalidate).toHaveBeenCalledTimes(2);
	});

	it("keeps a resources hint inside its own integration instead of sweeping the siblings", () => {
		const queryClient = new QueryClient();
		queryClient.setQueryData(
			getIntegrationCatalogQueryKey({ path: { workspaceSlug: WORKSPACE } }),
			[
				{ kind: "OUTLINE", connected: true, connectionId: CONNECTION_ID, displayName: "Outline" },
				{ kind: "SLACK", connected: true, connectionId: 99, displayName: "Slack" },
			],
		);
		const invalidate = vi.spyOn(queryClient, "invalidateQueries");
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(queryClient) });

		act(() => latestSource().emit("resources"));
		flushHintDebounce();

		expect(invalidate).toHaveBeenCalledWith({
			queryKey: listConnectionSyncResourcesQueryKey({
				path: { workspaceSlug: WORKSPACE, connectionId: CONNECTION_ID },
			}),
		});
		expect(invalidate).toHaveBeenCalledWith({
			queryKey: listOutlineCollectionsQueryKey({ path: { workspaceSlug: WORKSPACE } }),
		});
		// The hint belongs to the Outline connection; Slack's channel list has nothing to do with it.
		expect(invalidate).not.toHaveBeenCalledWith({
			queryKey: listSlackChannelsQueryKey({ path: { workspaceSlug: WORKSPACE } }),
		});
	});

	it("falls back to both catalogs when the connection's kind is not cached yet", () => {
		const queryClient = new QueryClient();
		const invalidate = vi.spyOn(queryClient, "invalidateQueries");
		renderHook(() => useSyncEvents(WORKSPACE), { wrapper: wrapper(queryClient) });

		act(() => latestSource().emit("resources"));
		flushHintDebounce();

		expect(invalidate).toHaveBeenCalledWith({
			queryKey: listOutlineCollectionsQueryKey({ path: { workspaceSlug: WORKSPACE } }),
		});
		expect(invalidate).toHaveBeenCalledWith({
			queryKey: listSlackChannelsQueryKey({ path: { workspaceSlug: WORKSPACE } }),
		});
	});

	it("closes the stream and stops reconnecting on unmount", () => {
		const { unmount } = renderHook(() => useSyncEvents(WORKSPACE), {
			wrapper: wrapper(new QueryClient()),
		});
		const source = latestSource();
		act(() => source.onopen?.());
		act(() => source.fail());

		unmount();
		runReconnectBackoff();

		expect(source.closed).toBe(true);
		expect(FakeEventSource.instances).toHaveLength(1);
	});
});
