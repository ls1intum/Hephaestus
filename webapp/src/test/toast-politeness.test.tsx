import { render, screen } from "@testing-library/react";
import { toast } from "sonner";
import { describe, expect, it } from "vitest";
import { Toaster } from "@/components/ui/sonner";

/**
 * A tripwire on the dependency, not on our own code: sonner has no per-toast politeness, so every
 * toast is polite. These fail when upstream fixes that — then route `toast.error` to an assertive
 * region (emilkowalski/sonner#765) and delete this file.
 */
describe("sonner toast politeness", () => {
	it("announces an error toast politely, because there is no per-toast role to set", async () => {
		render(<Toaster />);
		toast.error("Could not delete the model");

		await screen.findByText("Could not delete the model");
		const region = document.querySelector("section[aria-live]");
		expect(region?.getAttribute("aria-live")).toBe("polite");

		expect(screen.queryByRole("alert")).toBeNull();
	});
});
