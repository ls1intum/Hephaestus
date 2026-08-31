import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Textarea } from "@/components/ui/textarea";

import type { Survey } from "@/api/types.gen";

interface ActiveSurveyDialogProps {
	survey?: Survey;
	isSubmitting: boolean;
	isDismissing: boolean;
	onSubmit: (answers: Record<string, string>) => void;
	onDismiss: () => void;
}

export function ActiveSurveyDialog({
	survey,
	isSubmitting,
	isDismissing,
	onSubmit,
	onDismiss,
}: ActiveSurveyDialogProps) {
	const [answers, setAnswers] = useState<Record<string, string>>({});
	// Escape, the close button and a backdrop click mean "not now": hide until the next page load.
	// Only the explicit "Don't ask me again" button records the permanent, irreversible dismissal.
	const [snoozed, setSnoozed] = useState(false);
	if (!survey || snoozed) return null;
	const complete = survey.questions.every(
		(question) => !question.required || answers[question.id]?.trim(),
	);
	return (
		<Dialog
			open
			onOpenChange={(nextOpen) => {
				if (!nextOpen && !isDismissing && !isSubmitting) setSnoozed(true);
			}}
		>
			<DialogContent>
				<DialogHeader>
					<DialogTitle>{survey.title}</DialogTitle>
					<DialogDescription>
						{survey.description} Your response is stored on this instance for its administrators. It
						is used only for product feedback, not research. Contact your instance administrator to
						object to or request deletion of your response.
					</DialogDescription>
				</DialogHeader>
				<form
					className="space-y-4"
					onSubmit={(event) => {
						event.preventDefault();
						onSubmit(answers);
					}}
				>
					{survey.questions.map((question) => (
						<fieldset className="space-y-2" key={question.id}>
							<legend className="text-sm font-medium" id={`survey-${question.id}-legend`}>
								{question.prompt}
								{question.required ? " (required)" : ""}
							</legend>
							{question.type === "TEXT" ? (
								<>
									<Label className="sr-only" htmlFor={`survey-${question.id}`}>
										{question.prompt}
									</Label>
									<Textarea
										id={`survey-${question.id}`}
										name={question.id}
										required={question.required}
										maxLength={4000}
										value={answers[question.id] ?? ""}
										onChange={(event) =>
											setAnswers((current) => ({ ...current, [question.id]: event.target.value }))
										}
									/>
								</>
							) : (
								<RadioGroup
									aria-labelledby={`survey-${question.id}-legend`}
									className="flex flex-wrap gap-3"
									name={question.id}
									value={answers[question.id] ?? ""}
									onValueChange={(value) =>
										setAnswers((current) => ({ ...current, [question.id]: value }))
									}
								>
									{(question.type === "RATING" ? ["1", "2", "3", "4", "5"] : question.options).map(
										(option) => (
											<div className="flex items-center gap-2" key={option}>
												<RadioGroupItem id={`survey-${question.id}-${option}`} value={option} />
												<Label htmlFor={`survey-${question.id}-${option}`}>{option}</Label>
											</div>
										),
									)}
								</RadioGroup>
							)}
						</fieldset>
					))}
					<div className="flex justify-between">
						<Button type="button" variant="ghost" disabled={isDismissing} onClick={onDismiss}>
							Don't ask me again
						</Button>
						<Button type="submit" disabled={!complete || isSubmitting || isDismissing}>
							{isSubmitting ? "Submitting…" : "Submit"}
						</Button>
					</div>
				</form>
			</DialogContent>
		</Dialog>
	);
}
