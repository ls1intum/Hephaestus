import { render, screen, waitFor } from "@testing-library/react";
import { toast } from "sonner";
import { describe, expect, it } from "vitest";
import { Toaster } from "@/components/ui/sonner";

/**
 * A tripwire on the dependency, not a test of our own code: sonner offers no way to announce an
 * error toast assertively, so every toast in the app is polite. These fail *when upstream fixes
 * that* — then route `toast.error` to an assertive region (emilkowalski/sonner#765) and delete this
 * file.
 */
describe("sonner toast politeness", () => {
	it("announces an error toast politely, because there is no per-toast role to set", async () => {
		render(<Toaster />);
		toast.error("Could not delete the model");

		await screen.findByText("Could not delete the model");
		const region = document.querySelector("section[aria-live]");
		expect(region?.getAttribute("aria-live")).toBe("polite");

		// Nor does the toast announce itself: the day sonner gives it a role, this fails.
		expect(screen.queryByRole("alert")).toBeNull();
	});

	it("drops an aria-live override rather than forwarding it to the container", async () => {
		// The cast smuggles a prop past `ToasterProps`, which has none for politeness.
		render(<Toaster {...({ "aria-live": "assertive" } as object)} />);
		toast.error("Could not save the model");

		await waitFor(() => expect(document.querySelector("section[aria-live]")).not.toBeNull());
		expect(document.querySelector("section[aria-live]")?.getAttribute("aria-live")).toBe("polite");
	});
});
