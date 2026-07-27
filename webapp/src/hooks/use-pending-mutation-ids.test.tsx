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

/** A generated `@hey-api` helper as it would look if the generator started keying mutations itself. */
function generatedMutation(): UseMutationOptions<void, Error, Vars> {
	return {
		mutationKey: ["generated", "its", "own", "key"],
		mutationFn: (_variables: Vars) => new Promise<void>(() => {}),
	};
}

function Harness({ releaseFast }: { releaseFast: Promise<void> }) {
	const slow = useMutation({
		mutationKey: [...KEY, "slow"],
		mutationFn: (_variables: Vars) => new Promise<void>(() => {}),
	});
	const fast = useMutation({
		mutationKey: [...KEY, "fast"],
		mutationFn: (_variables: Vars) => releaseFast,
	});
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

// `<output>` carries an implicit `role="status"`, so the readout needs no test id.
const pendingIds = () => screen.getByRole("status").textContent;
const click = (name: string) => fireEvent.click(screen.getByRole("button", { name }));

describe("usePendingMutationIds", () => {
	it("reports nothing while nothing is in flight", () => {
		renderHarness();
		expect(pendingIds()).toBe("");
	});

	it("keeps reporting a call that is still running after a sibling settles", async () => {
		const releaseFast = renderHarness();

		click("start slow");
		await waitFor(() => expect(pendingIds()).toBe("1"));

		click("start fast");
		await waitFor(() => expect(pendingIds()).toBe("1,2"));

		releaseFast();
		await waitFor(() => expect(pendingIds()).toBe("1"));
	});

	it("still finds a call whose generated helper brought a mutation key of its own", async () => {
		renderHarness();

		click("start generated");

		await waitFor(() => expect(pendingIds()).toBe("3"));
	});
});

describe("generated mutation helpers", () => {
	it("still file no mutation key of their own, so every filedUnder key is the only one", () => {
		// A canary, not a requirement — `filedUnder` is correct either way. When this fails, every
		// `useMutation` that files no key of its own has joined an unreviewed key space.
		expect(adminUpdateLlmConnectionMutation()).not.toHaveProperty("mutationKey");
		expect(adminUpdateLlmConnectionMutation().mutationFn).toEqual(expect.any(Function));
	});
});
