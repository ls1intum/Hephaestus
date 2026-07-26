import {
	QueryClient,
	QueryClientProvider,
	type UseMutationOptions,
	useMutation,
} from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { adminUpdateLlmConnectionMutation } from "@/api/@tanstack/react-query.gen";
import { filedUnder, usePendingMutationIds } from "./use-pending-mutation-ids";

type Vars = { id: number };

const KEY = ["thing"];

/**
 * Stands in for a generated `@hey-api` mutation helper — with the one difference that matters here:
 * it supplies a `mutationKey` of its own, which is what the generator is free to start doing.
 */
function generatedMutation(): UseMutationOptions<void, Error, Vars> {
	return {
		mutationKey: ["generated", "its", "own", "key"],
		mutationFn: (_variables: Vars) => new Promise<void>(() => {}),
	};
}

/** Two mutations filed under one prefix — the shape a panel uses for update and delete of a row. */
function Harness({ releaseFast }: { releaseFast: Promise<void> }) {
	const slow = useMutation({
		mutationKey: [...KEY, "slow"],
		mutationFn: (_variables: Vars) => new Promise<void>(() => {}),
	});
	const fast = useMutation({
		mutationKey: [...KEY, "fast"],
		mutationFn: (_variables: Vars) => releaseFast,
	});
	// Filed the way every route files a generated mutation, against a helper that brings its own key.
	const generated = useMutation({
		...filedUnder([...KEY, "generated"], generatedMutation()),
	});
	const pending = usePendingMutationIds<Vars>(KEY, (variables) => variables.id);

	return (
		<>
			<button type="button" onClick={() => slow.mutate({ id: 1 })}>
				start slow
			</button>
			<button type="button" onClick={() => fast.mutate({ id: 2 })}>
				start fast
			</button>
			<button type="button" onClick={() => generated.mutate({ id: 3 })}>
				start generated
			</button>
			<output>{[...pending].sort().join(",")}</output>
		</>
	);
}

function renderHarness() {
	const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
	let release: () => void = () => {};
	const releaseFast = new Promise<void>((resolve) => {
		release = resolve;
	});
	render(
		<QueryClientProvider client={client}>
			<Harness releaseFast={releaseFast} />
		</QueryClientProvider>,
	);
	return () => release();
}

/** `<output>` carries an implicit `role="status"`, so the readout needs no test id to be found. */
const pendingIds = () => screen.getByRole("status").textContent;
const click = (name: string) => fireEvent.click(screen.getByRole("button", { name }));

describe("usePendingMutationIds", () => {
	it("reports nothing while nothing is in flight", () => {
		renderHarness();
		expect(pendingIds()).toBe("");
	});

	it("keeps reporting a call that is still running after a sibling settles", async () => {
		// The whole point: one `useState("which row is busy")` would be cleared by the fast call
		// settling, putting the slow row back to looking idle while its request is still out.
		const releaseFast = renderHarness();

		click("start slow");
		await waitFor(() => expect(pendingIds()).toBe("1"));

		click("start fast");
		await waitFor(() => expect(pendingIds()).toBe("1,2"));

		releaseFast();
		await waitFor(() => expect(pendingIds()).toBe("1"));
	});

	it("still finds a call whose generated helper brought a mutation key of its own", async () => {
		// Filed the other way round — `{ mutationKey: KEY, ...generatedMutation() }` — the helper's key
		// would win, this call would fall outside the prefix, and the row it belongs to would look idle
		// with its request still out. Nothing else on the screen would change, so nothing else can
		// catch it.
		renderHarness();

		click("start generated");

		await waitFor(() => expect(pendingIds()).toBe("3"));
	});
});

describe("generated mutation helpers", () => {
	it("still file no mutation key of their own, so every filedUnder key is the only one", () => {
		// A canary, not a requirement: `filedUnder` is correct either way. But the day this fails the
		// generator has started keying mutations itself, and every `useMutation` on the branch that
		// files no key of its own silently joins a key space it was never reviewed against.
		//
		// Scoped to that one property. An `toEqual({ mutationFn })` here would also fail on a `meta`,
		// `retry` or `networkMode` the generator starts emitting — none of which touch key space, so
		// the failure would not mean what the sentence above says it means.
		expect(adminUpdateLlmConnectionMutation()).not.toHaveProperty("mutationKey");
		expect(adminUpdateLlmConnectionMutation().mutationFn).toEqual(expect.any(Function));
	});
});
