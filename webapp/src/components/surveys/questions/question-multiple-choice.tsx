import { useEffect, useState } from "react";

import { Checkbox } from "@/components/ui/checkbox";
import {
	Field,
	FieldContent,
	FieldError,
	FieldGroup,
	FieldLabel,
	FieldLegend,
	FieldSet,
	FieldTitle,
} from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import type { SurveyQuestion, SurveyResponse } from "@/types/survey";
import { QuestionDescription } from "../question-description";

type QuestionMultipleChoiceQuestion = Pick<
	SurveyQuestion,
	| "id"
	| "question"
	| "description"
	| "descriptionContentType"
	| "required"
	| "choices"
	| "hasOpenChoice"
>;

export interface QuestionMultipleChoiceProps
	extends QuestionMultipleChoiceQuestion {
	value: SurveyResponse;
	onChange: (value: string[]) => void;
	error?: string;
}

export function QuestionMultipleChoice({
	id,
	question: prompt,
	description,
	descriptionContentType = "text",
	required,
	choices = [],
	hasOpenChoice = false,
	value,
	onChange,
	error,
}: QuestionMultipleChoiceProps) {
	const selectedValues = Array.isArray(value) ? value : [];
	const groupId = `survey-question-${id}`;
	const hasCustomOption = hasOpenChoice && choices.length > 0;
	const baseChoices = hasCustomOption ? choices.slice(0, -1) : choices;
	const openChoiceLabel = hasCustomOption
		? choices[choices.length - 1]
		: undefined;
	const derivedCustomValue = hasCustomOption
		? selectedValues.find((candidate) => !baseChoices.includes(candidate))
		: undefined;
	const [customSelected, setCustomSelected] = useState(
		Boolean(derivedCustomValue),
	);
	const [customValue, setCustomValue] = useState(derivedCustomValue ?? "");

	useEffect(() => {
		if (!hasCustomOption) {
			if (customSelected || customValue !== "") {
				setCustomSelected(false);
				setCustomValue("");
			}
			return;
		}

		if (derivedCustomValue !== undefined) {
			if (!customSelected) {
				setCustomSelected(true);
			}
			if (customValue !== derivedCustomValue) {
				setCustomValue(derivedCustomValue);
			}
			return;
		}

		if (!customSelected && customValue !== "") {
			setCustomValue("");
		}
	}, [customSelected, customValue, derivedCustomValue, hasCustomOption]);

	const baseSelections = selectedValues.filter((choice) =>
		baseChoices.includes(choice),
	);

	const toggleChoice = (choice: string, checked: boolean | string) => {
		const isChecked = checked === true || checked === "indeterminate";
		if (isChecked) {
			onChange(Array.from(new Set([...selectedValues, choice])));
			return;
		}

		onChange(selectedValues.filter((item) => item !== choice));
	};

	const handleCustomToggle = (checked: boolean | string) => {
		const isChecked = checked === true || checked === "indeterminate";
		setCustomSelected(isChecked);
		if (!isChecked) {
			onChange(baseSelections);
			return;
		}

		if (customValue.length > 0) {
			onChange(Array.from(new Set([...baseSelections, customValue])));
		} else {
			onChange(baseSelections);
		}
	};

	const handleCustomValueChange = (next: string) => {
		setCustomSelected(true);
		setCustomValue(next);
		if (next.length === 0) {
			onChange(baseSelections);
			return;
		}
		onChange(Array.from(new Set([...baseSelections, next])));
	};

	return (
		<FieldSet
			data-invalid={error ? "true" : undefined}
			aria-invalid={Boolean(error)}
			aria-describedby={error ? `${groupId}-error` : undefined}
		>
			<FieldLegend>
				{prompt}
				{required && <span className="text-destructive ml-1">*</span>}
			</FieldLegend>
			<QuestionDescription
				description={description}
				descriptionContentType={descriptionContentType}
			/>
			<FieldGroup data-slot="checkbox-group">
				{baseChoices.map((choice, index) => {
					const id = `${groupId}-choice-${index}`;
					const isChecked = selectedValues.includes(choice);
					return (
						<FieldLabel key={id} htmlFor={id}>
							<Field orientation="horizontal">
								<FieldContent>
									<FieldTitle>{choice}</FieldTitle>
								</FieldContent>
								<Checkbox
									id={id}
									checked={isChecked}
									onCheckedChange={(checked) => toggleChoice(choice, checked)}
								/>
							</Field>
						</FieldLabel>
					);
				})}
				{hasCustomOption &&
					openChoiceLabel &&
					(() => {
						const openId = `${groupId}-choice-open`;
						const textAreaId = `${openId}-input`;
						return (
							<FieldLabel key={openId} htmlFor={openId}>
								<Field orientation="horizontal">
									<FieldContent>
										<FieldTitle>{openChoiceLabel}</FieldTitle>
										<Textarea
											id={textAreaId}
											value={customValue}
											onChange={(event) =>
												handleCustomValueChange(event.target.value)
											}
											onFocus={() => setCustomSelected(true)}
											placeholder="Share your answer"
											className="mt-2 min-h-[32px] bg-background/90"
										/>
									</FieldContent>
									<Checkbox
										id={openId}
										checked={customSelected}
										onCheckedChange={handleCustomToggle}
									/>
								</Field>
							</FieldLabel>
						);
					})()}
			</FieldGroup>
			{error && <FieldError id={`${groupId}-error`}>{error}</FieldError>}
		</FieldSet>
	);
}
