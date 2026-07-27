import {
	Field,
	FieldContent,
	FieldDescription,
	FieldLabel,
	FieldTitle,
} from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";

/**
 * Who may use an instance catalog model. One vocabulary for the question, wherever it is asked.
 *
 * Named for the choice rather than for the wire (`LlmModel["visibility"]` is `PUBLIC` / `GRANTED`),
 * because the wire words are about storage and these two are about people.
 */
export type ModelAccessScope = "ALL" | "SELECTED";

export interface ModelAccessScopeChoiceProps {
	/** Prefixes the two radio ids, so the create form and the access editor cannot collide. */
	idPrefix: string;
	/**
	 * Names the group. A `role="radiogroup"` element is not named by an enclosing legend, so this is
	 * needed even where a visible legend sits above it.
	 */
	label: string;
	value: ModelAccessScope;
	onChange: (value: ModelAccessScope) => void;
}

/**
 * The "who can use this model" choice, shared by the create form and the dedicated access editor.
 *
 * Radios, not a `<Select>`: there are exactly two answers and each one has a consequence a reader
 * has to weigh before choosing. A select collapses the alternative — and its consequence — behind a
 * popup, so the admin picks between two words instead of between two outcomes. Both screens of one
 * feature asking the same question two ways is how an admin ends up believing they are two settings.
 */
export function ModelAccessScopeChoice({
	idPrefix,
	label,
	value,
	onChange,
}: ModelAccessScopeChoiceProps) {
	return (
		<RadioGroup
			value={value}
			onValueChange={(next) => {
				if (next) onChange(next as ModelAccessScope);
			}}
			className="gap-3"
			aria-label={label}
		>
			<FieldLabel htmlFor={`${idPrefix}-all`}>
				<Field orientation="horizontal">
					<FieldContent>
						<FieldTitle>All workspaces</FieldTitle>
						<FieldDescription>
							Every current and future workspace can select the model.
						</FieldDescription>
					</FieldContent>
					<RadioGroupItem id={`${idPrefix}-all`} value="ALL" />
				</Field>
			</FieldLabel>
			<FieldLabel htmlFor={`${idPrefix}-selected`}>
				<Field orientation="horizontal">
					<FieldContent>
						<FieldTitle>Selected workspaces</FieldTitle>
						<FieldDescription>
							Only explicitly selected workspaces can select the model.
						</FieldDescription>
					</FieldContent>
					<RadioGroupItem id={`${idPrefix}-selected`} value="SELECTED" />
				</Field>
			</FieldLabel>
		</RadioGroup>
	);
}
