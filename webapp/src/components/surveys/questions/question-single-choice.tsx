import { useState } from "react";
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
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Textarea } from "@/components/ui/textarea";
import type { SurveyQuestion, SurveyResponse } from "@/types/survey";
import { QuestionDescription } from "../question-description";

type QuestionSingleChoiceQuestion = Pick<
	SurveyQuestion,
	| "id"
	| "question"
	| "description"
	| "descriptionContentType"
	| "required"
	| "choices"
	| "hasOpenChoice"
>;

export interface QuestionSingleChoiceProps extends QuestionSingleChoiceQuestion {
	value: SurveyResponse;
	onChange: (value: string) => void;
	error?: string;
}

export function QuestionSingleChoice({
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
}: QuestionSingleChoiceProps) {
	const stringValue = typeof value === "string" ? value : "";
	const groupId = `survey-question-${id}`;
	const hasCustomOption = hasOpenChoice && choices.length > 0;
	const baseChoices = hasCustomOption ? choices.slice(0, -1) : choices;
	const openChoiceLabel = hasCustomOption ? choices[choices.length - 1] : undefined;
	const isKnownValue = stringValue !== "" && baseChoices.includes(stringValue);

	const [customSelected, setCustomSelected] = useState(
		() => hasCustomOption && stringValue !== "" && !baseChoices.includes(stringValue),
	);
	const [customValue, setCustomValue] = useState(() =>
		hasCustomOption && stringValue !== "" && !baseChoices.includes(stringValue) ? stringValue : "",
	);

	const customRadioValue = "__custom__";
	const openChoiceId = `${groupId}-choice-open`;
	const openChoiceInputId = `${openChoiceId}-input`;

	// The open choice is driven by the user, but the answer can also arrive from the outside
	// (restored draft, question swapped in). Reconcile the two while rendering so the field
	// never paints an answer the parent no longer holds. An empty answer is left alone: the
	// custom option stays selected without input so users can type later.
	if (!hasCustomOption || isKnownValue) {
		if (customSelected) {
			setCustomSelected(false);
		}
		if (customValue !== "") {
			setCustomValue("");
		}
	} else if (stringValue !== "") {
		if (!customSelected) {
			setCustomSelected(true);
		}
		if (customValue !== stringValue) {
			setCustomValue(stringValue);
		}
	}

	const radioGroupValue = customSelected ? customRadioValue : isKnownValue ? stringValue : "";

	const handleSelection = (nextValue: string) => {
		if (nextValue === customRadioValue) {
			setCustomSelected(true);
			onChange(customValue);
			return;
		}

		setCustomSelected(false);
		setCustomValue("");
		onChange(nextValue);
	};

	const handleCustomValueChange = (next: string) => {
		setCustomSelected(true);
		setCustomValue(next);
		onChange(next);
	};

	const handleCustomFocus = () => {
		if (!customSelected) {
			setCustomSelected(true);
			onChange(customValue);
		}
	};

	return (
		<FieldSet
			data-invalid={error ? "true" : undefined}
			aria-invalid={Boolean(error)}
			aria-describedby={error ? `${groupId}-error` : undefined}
		>
			<FieldLegend>
				{prompt}
				{required && <span className="text-destructive ml-0.5">*</span>}
			</FieldLegend>
			<QuestionDescription
				description={description}
				descriptionContentType={descriptionContentType}
			/>
			<FieldGroup data-slot="radio-group">
				<RadioGroup
					value={radioGroupValue}
					onValueChange={handleSelection}
					id={groupId}
					aria-invalid={Boolean(error)}
					aria-describedby={error ? `${groupId}-error` : undefined}
				>
					{baseChoices.map((choice, index) => {
						const choiceId = `${groupId}-choice-${index}`;
						return (
							<FieldLabel key={choiceId} htmlFor={choiceId}>
								<Field orientation="horizontal">
									<FieldContent>
										<FieldTitle>{choice}</FieldTitle>
									</FieldContent>
									<RadioGroupItem value={choice} id={choiceId} />
								</Field>
							</FieldLabel>
						);
					})}
					{hasCustomOption && openChoiceLabel && (
						<FieldLabel key={openChoiceId} htmlFor={openChoiceId}>
							<Field orientation="horizontal">
								<FieldContent>
									<FieldTitle>{openChoiceLabel}</FieldTitle>
									<Textarea
										id={openChoiceInputId}
										value={customValue}
										onFocus={handleCustomFocus}
										onChange={(event) => handleCustomValueChange(event.target.value)}
										placeholder="Share your answer"
										className="mt-2 min-h-[32px] bg-background/90"
									/>
								</FieldContent>
								<RadioGroupItem value={customRadioValue} id={openChoiceId} />
							</Field>
						</FieldLabel>
					)}
				</RadioGroup>
			</FieldGroup>
			{error && <FieldError id={`${groupId}-error`}>{error}</FieldError>}
		</FieldSet>
	);
}
