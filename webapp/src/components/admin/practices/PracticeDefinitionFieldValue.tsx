import type { PracticeDefinition } from "@/api/types.gen";
import {
	PracticeGuideVersion,
	PracticeVisualVersion,
} from "@/components/admin/practice-editor/practice-guidance-version";
import {
	definitionFieldText,
	type PracticeDefinitionField,
} from "@/components/admin/practices/practice-definition-fields";

export interface PracticeDefinitionFieldValueProps {
	definition: PracticeDefinition;
	field: PracticeDefinitionField;
}

export function PracticeDefinitionFieldValue({
	definition,
	field,
}: PracticeDefinitionFieldValueProps) {
	const { visual, guide } = definition.guidance;
	return (
		<div className="flex flex-col gap-2">
			<pre className="max-h-48 overflow-auto font-sans text-xs break-words whitespace-pre-wrap">
				{definitionFieldText(definition, field)}
			</pre>
			{field === "GUIDANCE" && visual !== null && <PracticeVisualVersion visual={visual} />}
			{field === "GUIDANCE" && guide !== null && <PracticeGuideVersion guide={guide} />}
		</div>
	);
}
