import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { PracticeGroupReviewObservation } from "@/api/types.gen";

import { ReviewObservationRow } from "./ReviewObservationRow";

const observation: PracticeGroupReviewObservation = {
	observationId: "00000000-0000-0000-0000-000000000001",
	feedbackId: "00000000-0000-0000-0000-000000000002",
	practiceSlug: "explain-decisions",
	practiceName: "Explain significant decisions",
	title: "The reason for the timeout is missing",
	presence: "PRESENT",
	assessment: "BAD",
	severity: "MINOR",
};

describe("ReviewObservationRow", () => {
	it("submits a new dispute only after its required explanation is available", () => {
		const onRespond = vi.fn();
		render(
			<ul>
				<ReviewObservationRow
					observation={observation}
					isOpen
					onToggle={vi.fn()}
					onRespond={onRespond}
				/>
			</ul>,
		);

		fireEvent.click(screen.getByRole("button", { name: "Disputed" }));
		expect(onRespond).not.toHaveBeenCalled();

		const explanation = screen.getByRole("textbox", { name: "Why do you disagree?" });
		fireEvent.change(explanation, {
			target: { value: "The timeout is required by the provider." },
		});
		fireEvent.click(screen.getByRole("button", { name: "Save comment" }));

		expect(onRespond).toHaveBeenCalledExactlyOnceWith(observation, {
			comment: "The timeout is required by the provider.",
			resolution: "DISPUTED",
		});
	});
});
