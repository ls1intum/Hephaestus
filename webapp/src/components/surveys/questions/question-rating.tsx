import { Button } from "@/components/ui/button";
import {
	Field,
	FieldContent,
	FieldDescription,
	FieldError,
	FieldTitle,
} from "@/components/ui/field";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import type { SurveyQuestion, SurveyResponse } from "@/types/survey";
import { QuestionDescription } from "../question-description";

type QuestionRatingQuestion = Pick<
	SurveyQuestion,
	| "id"
	| "question"
	| "description"
	| "descriptionContentType"
	| "required"
	| "display"
	| "scale"
	| "lowerBoundLabel"
	| "upperBoundLabel"
>;

export interface QuestionRatingProps extends QuestionRatingQuestion {
	value: SurveyResponse;
	onChange: (value: number) => void;
	error?: string;
}

const EMOJI_RATINGS = ["😡", "🙁", "😐", "🙂", "🤩"];

const getEmojiForRating = (rating: number, scale: number) => {
	if (scale <= 1) {
		return EMOJI_RATINGS[0];
	}

	const ratio = (rating - 1) / (scale - 1);
	const emojiIndex = Math.round(ratio * (EMOJI_RATINGS.length - 1));
	return EMOJI_RATINGS[Math.min(EMOJI_RATINGS.length - 1, Math.max(0, emojiIndex))];
};

export function QuestionRating({
	question: prompt,
	description,
	descriptionContentType = "text",
	required,
	display,
	scale,
	lowerBoundLabel,
	upperBoundLabel,
	value,
	onChange,
	error,
}: QuestionRatingProps) {
	const isEmojiDisplay = display === "emoji";
	const ratingScale = scale ?? (isEmojiDisplay ? EMOJI_RATINGS.length : 10);
	const numericValue = typeof value === "number" ? value : null;

	const ratings = Array.from({ length: ratingScale }, (_, index) => index + 1);

	const handleNumericChange = (nextValue: string) => {
		if (!nextValue) {
			return;
		}

		const parsed = Number(nextValue);
		if (!Number.isNaN(parsed)) {
			onChange(parsed);
		}
	};

	const renderBounds = () => {
		if (!lowerBoundLabel && !upperBoundLabel) {
			return null;
		}

		return (
			<FieldDescription className="flex w-full justify-between text-xs text-muted-foreground gap-8">
				<span>{lowerBoundLabel}</span>
				<span>{upperBoundLabel}</span>
			</FieldDescription>
		);
	};

	return (
		<Field data-invalid={error ? "true" : undefined}>
			<FieldTitle>
				{prompt}
				{required && <span className="text-destructive ml-0.5">*</span>}
			</FieldTitle>
			<FieldContent>
				<QuestionDescription
					description={description}
					descriptionContentType={descriptionContentType}
				/>
				{isEmojiDisplay ? (
					<div className="flex flex-col items-center gap-2 mx-auto mt-3">
						<div className="flex flex-wrap justify-center gap-2">
							{ratings.map((rating) => {
								const isSelected = numericValue === rating;
								return (
									<Button
										key={rating}
										variant="outline"
										size="lg"
										className={`h-14 w-14 p-0 text-2xl ${
											isSelected ? "border-primary bg-background shadow-sm" : ""
										}`}
										type="button"
										onClick={() => onChange(rating)}
										aria-pressed={isSelected}
										aria-label={`Rating ${rating} out of ${ratingScale}`}
									>
										{getEmojiForRating(rating, ratingScale)}
									</Button>
								);
							})}
						</div>
						{renderBounds()}
					</div>
				) : (
					<div className="mx-auto mt-3 space-y-2">
						<ToggleGroup
							type="single"
							value={numericValue !== null ? String(numericValue) : undefined}
							onValueChange={handleNumericChange}
							variant="outline"
							size="sm"
							className="flex flex-nowrap"
						>
							{ratings.map((rating) => (
								<ToggleGroupItem
									key={rating}
									value={String(rating)}
									aria-label={`Rating ${rating} out of ${ratingScale}`}
									className="data-[state=on]:border-primary data-[state=on]:bg-background data-[state=on]:text-primary data-[state=on]:shadow-sm data-[state=on]:data-[spacing=0]:data-[variant=outline]:border-l-1 not-first:data-[state=on]:ml-[-1px]"
								>
									{rating}
								</ToggleGroupItem>
							))}
						</ToggleGroup>
						{renderBounds()}
					</div>
				)}
				{error && <FieldError>{error}</FieldError>}
			</FieldContent>
		</Field>
	);
}
