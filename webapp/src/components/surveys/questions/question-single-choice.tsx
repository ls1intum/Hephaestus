import { useEffect, useState } from "react";
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

export interface QuestionSingleChoiceProps
	extends QuestionSingleChoiceQuestion {
	value: SurveyResponse;
	onChange: (value: string) => void;
	error?: string;
}

/**
 * Renders a single-choice survey question with an optional open-ended (custom) choice.
 *
 * @param id - Unique question identifier used to construct element ids for accessibility.
 * @param question - The question prompt displayed in the field legend.
 * @param description - Optional supplemental description displayed under the legend.
 * @param descriptionContentType - Content type of `description` (defaults to `"text"`).
 * @param required - If true, displays a required indicator next to the prompt.
 * @param choices - Array of choice labels. When `hasOpenChoice` is true, the last item is treated as the open-choice label and the preceding items are the base choices.
 * @param hasOpenChoice - When true, renders an additional radio option paired with a textarea to accept a custom answer.
 * @param value - Current response value; a non-empty string not present in base choices is treated as the custom answer.
 * @param onChange - Called with the updated response string whenever the selected choice or custom input changes.
 * @param error - Optional error message; when present the fieldset is marked invalid and the message is displayed.
 * @returns A React element containing the labeled radio group (and optional custom textarea) for the question.
 */
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
	const openChoiceLabel = hasCustomOption
		? choices[choices.length - 1]
		: undefined;
	const isKnownValue = stringValue !== "" && baseChoices.includes(stringValue);

	const [customSelected, setCustomSelected] = useState(
		() =>
			hasCustomOption &&
			stringValue !== "" &&
			!baseChoices.includes(stringValue),
	);
	const [customValue, setCustomValue] = useState(() =>
		hasCustomOption && stringValue !== "" && !baseChoices.includes(stringValue)
			? stringValue
			: "",
	);

	const customRadioValue = "__custom__";
	const openChoiceId = `${groupId}-choice-open`;
	const openChoiceInputId = `${openChoiceId}-input`;

	useEffect(() => {
		if (!hasCustomOption) {
			if (customSelected || customValue !== "") {
				setCustomSelected(false);
				setCustomValue("");
			}
			return;
		}

		const baseChoicesForEffect = hasCustomOption
			? choices.slice(0, -1)
			: choices;
		const isCustomAnswer =
			stringValue !== "" && !baseChoicesForEffect.includes(stringValue);

		if (isCustomAnswer) {
			if (!customSelected) {
				setCustomSelected(true);
			}
			if (customValue !== stringValue) {
				setCustomValue(stringValue);
			}
			return;
		}

		if (stringValue === "") {
			// Keep the custom option selected even without input so users can type later.
			return;
		}

		const isBaseChoice = baseChoicesForEffect.includes(stringValue);
		if (isBaseChoice) {
			if (customSelected) {
				setCustomSelected(false);
			}
			if (customValue !== "") {
				setCustomValue("");
			}
			return;
		}

		// Fallback when value is cleared externally but selection was not reset.
		if (customSelected && customValue !== "") {
			setCustomValue("");
		}
	}, [choices, customSelected, customValue, hasCustomOption, stringValue]);

	const radioGroupValue = customSelected
		? customRadioValue
		: isKnownValue
			? stringValue
			: "";

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
				{required && <span className="text-destructive ml-1">*</span>}
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
										onChange={(event) =>
											handleCustomValueChange(event.target.value)
										}
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