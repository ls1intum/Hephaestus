import { render, screen, waitFor } from "@testing-library/react";
import { toast } from "sonner";
import { describe, expect, it } from "vitest";
import { Toaster } from "@/components/ui/sonner";

/**
 * A tripwire on a dependency, not a test of our own code.
 *
 * `ui/sonner.tsx` carries the reasoning: sonner offers no way to announce an error toast
 * assertively, so every toast in the app is polite. That verdict is only trustworthy while it stays
 * true, and a silent `pnpm update` is exactly how it would stop being true without anyone noticing.
 * Each assertion below therefore fails *when upstream fixes this* — a red suite here means go read
 * emilkowalski/sonner#765, route `toast.error` to an assertive region, and delete this file.
 */
describe("sonner toast politeness", () => {
	it("announces an error toast politely, because there is no per-toast role to set", async () => {
		render(<Toaster />);
		toast.error("Could not delete the model");

		await screen.findByText("Could not delete the model");
		const region = document.querySelector("section[aria-live]");
		expect(region?.getAttribute("aria-live")).toBe("polite");

		// The toast itself is not a live region and carries no role, so it inherits the container's
		// politeness. Nothing about it says "error" to assistive tech beyond the message text.
		const item = document.querySelector("li[data-sonner-toast]");
		expect(item).not.toBeNull();
		expect(item?.getAttribute("role")).toBeNull();
		expect(item?.getAttribute("aria-live")).toBeNull();
	});

	it("drops an aria-live override rather than forwarding it to the container", async () => {
		// `ToasterProps` has no politeness prop, and the container is built from a closed set of
		// attributes with no rest-spread — so even smuggling one past the type system changes nothing.
		// If this ever lands on the DOM, sonner has gained the passthrough and the verdict is stale.
		render(<Toaster {...({ "aria-live": "assertive" } as object)} />);
		toast.error("Could not save the model");

		await waitFor(() => expect(document.querySelector("section[aria-live]")).not.toBeNull());
		expect(document.querySelector("section[aria-live]")?.getAttribute("aria-live")).toBe("polite");
	});
});
