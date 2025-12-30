/**
 * Tests for BadPracticeCard component.
 * Verifies that the Resolve button correctly shows/hides based on state.
 */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BadPracticeCard } from "./BadPracticeCard";

describe("BadPracticeCard", () => {
	const defaultProps = {
		id: 1,
		title: "Test Practice",
		description: "Test description",
	};

	describe("Resolve button visibility", () => {
		it("shows Resolve button for CRITICAL_ISSUE when user is dashboard user", () => {
			render(
				<BadPracticeCard {...defaultProps} state="CRITICAL_ISSUE" currUserIsDashboardUser={true} />,
			);

			const button = screen.queryByRole("button", { name: /resolve/i });
			expect(button).not.toBeNull();
		});

		it("shows Resolve button for NORMAL_ISSUE when user is dashboard user", () => {
			render(
				<BadPracticeCard {...defaultProps} state="NORMAL_ISSUE" currUserIsDashboardUser={true} />,
			);

			const button = screen.queryByRole("button", { name: /resolve/i });
			expect(button).not.toBeNull();
		});

		it("shows Resolve button for MINOR_ISSUE when user is dashboard user", () => {
			render(
				<BadPracticeCard {...defaultProps} state="MINOR_ISSUE" currUserIsDashboardUser={true} />,
			);

			const button = screen.queryByRole("button", { name: /resolve/i });
			expect(button).not.toBeNull();
		});

		it("hides Resolve button for already FIXED items", () => {
			render(<BadPracticeCard {...defaultProps} state="FIXED" currUserIsDashboardUser={true} />);

			const button = screen.queryByRole("button", { name: /resolve/i });
			expect(button).toBeNull();
		});

		it("hides Resolve button for WONT_FIX items", () => {
			render(<BadPracticeCard {...defaultProps} state="WONT_FIX" currUserIsDashboardUser={true} />);

			const button = screen.queryByRole("button", { name: /resolve/i });
			expect(button).toBeNull();
		});

		it("hides Resolve button for WRONG items", () => {
			render(<BadPracticeCard {...defaultProps} state="WRONG" currUserIsDashboardUser={true} />);

			const button = screen.queryByRole("button", { name: /resolve/i });
			expect(button).toBeNull();
		});

		it("hides Resolve button for GOOD_PRACTICE items", () => {
			render(
				<BadPracticeCard {...defaultProps} state="GOOD_PRACTICE" currUserIsDashboardUser={true} />,
			);

			const button = screen.queryByRole("button", { name: /resolve/i });
			expect(button).toBeNull();
		});

		it("hides Resolve button when user is not dashboard user", () => {
			render(
				<BadPracticeCard
					{...defaultProps}
					state="CRITICAL_ISSUE"
					currUserIsDashboardUser={false}
				/>,
			);

			const button = screen.queryByRole("button", { name: /resolve/i });
			expect(button).toBeNull();
		});
	});

	describe("Resolve actions", () => {
		it("calls onResolveBadPracticeAsFixed when 'Resolve as fixed' is clicked", async () => {
			const user = userEvent.setup();
			const onFixed = vi.fn();

			render(
				<BadPracticeCard
					{...defaultProps}
					state="CRITICAL_ISSUE"
					currUserIsDashboardUser={true}
					onResolveBadPracticeAsFixed={onFixed}
				/>,
			);

			// Open dropdown
			await user.click(screen.getByRole("button", { name: /resolve/i }));

			// Click resolve as fixed
			await user.click(screen.getByRole("menuitem", { name: /resolve as fixed/i }));

			expect(onFixed).toHaveBeenCalledWith(1);
		});

		it("calls onResolveBadPracticeAsWontFix when 'Resolve as won't fix' is clicked", async () => {
			const user = userEvent.setup();
			const onWontFix = vi.fn();

			render(
				<BadPracticeCard
					{...defaultProps}
					state="NORMAL_ISSUE"
					currUserIsDashboardUser={true}
					onResolveBadPracticeAsWontFix={onWontFix}
				/>,
			);

			await user.click(screen.getByRole("button", { name: /resolve/i }));
			await user.click(screen.getByRole("menuitem", { name: /resolve as won't fix/i }));

			expect(onWontFix).toHaveBeenCalledWith(1);
		});

		it("calls onResolveBadPracticeAsWrong when 'Resolve as wrong' is clicked", async () => {
			const user = userEvent.setup();
			const onWrong = vi.fn();

			render(
				<BadPracticeCard
					{...defaultProps}
					state="MINOR_ISSUE"
					currUserIsDashboardUser={true}
					onResolveBadPracticeAsWrong={onWrong}
				/>,
			);

			await user.click(screen.getByRole("button", { name: /resolve/i }));
			await user.click(screen.getByRole("menuitem", { name: /resolve as wrong/i }));

			expect(onWrong).toHaveBeenCalledWith(1);
		});
	});

	describe("Display", () => {
		it("displays the practice title", () => {
			render(<BadPracticeCard {...defaultProps} state="NORMAL_ISSUE" />);

			expect(screen.getByText("Test Practice")).not.toBeNull();
		});

		it("displays the practice description", () => {
			render(<BadPracticeCard {...defaultProps} state="NORMAL_ISSUE" />);

			expect(screen.getByText("Test description")).not.toBeNull();
		});
	});
});
