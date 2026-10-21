import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { InfiniteListEnd, type InfiniteListEndProps } from "./InfiniteListEnd";

type Report = (entries: Pick<IntersectionObserverEntry, "target" | "isIntersecting">[]) => void;

/** An `IntersectionObserver` that reports every element it observes as in view, at once. */
class InViewObserver {
	readonly #report: Report;

	constructor(report: Report) {
		this.#report = report;
	}

	observe(target: Element) {
		this.#report([{ target, isIntersecting: true }]);
	}

	unobserve = vi.fn();
	disconnect = vi.fn();
}

function end(props: Partial<InfiniteListEndProps>) {
	return (
		<InfiniteListEnd
			hasMore
			isLoadingMore={false}
			onLoadMore={vi.fn()}
			moreLabel="View earlier reviews"
			failedLabel="We could not load earlier reviews."
			loadingRow={null}
			{...props}
		/>
	);
}

describe("InfiniteListEnd", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("asks for the next page once a refresh that held it back ends", () => {
		vi.stubGlobal("IntersectionObserver", InViewObserver);
		const onLoadMore = vi.fn();

		const { rerender } = render(end({ isRefreshing: true, onLoadMore }));
		expect(onLoadMore).not.toHaveBeenCalled();

		rerender(end({ isRefreshing: false, onLoadMore }));
		expect(onLoadMore).toHaveBeenCalledOnce();
	});

	it("adds no placeholder while loading when the list draws its own", () => {
		vi.stubGlobal("IntersectionObserver", InViewObserver);
		const { container } = render(end({ isLoadingMore: true, loadingRow: null }));
		// Only the press stays: an empty placeholder would open a gap above it while the page loads.
		expect(container.querySelector("[aria-hidden]")).toBeNull();
		screen.getByRole("button", { name: "Loading…" });
	});

	it("shows the list's placeholder while the next page loads", () => {
		vi.stubGlobal("IntersectionObserver", InViewObserver);
		render(end({ isLoadingMore: true, loadingRow: <p>placeholder</p> }));
		screen.getByText("placeholder");
	});

	it("keeps focus at the end of the list when the last page removes the press", () => {
		vi.stubGlobal("IntersectionObserver", InViewObserver);
		const { container, rerender } = render(end({ isLoadingMore: true }));
		const press = screen.getByRole("button", { name: "Loading…" });
		press.focus();

		rerender(end({ hasMore: false }));

		expect(screen.queryByRole("button")).toBeNull();
		expect(document.activeElement).toBe(container.firstElementChild);
	});
});
