import type { CodeEditorProps } from "@/components/common/CodeEditor";

export function CodeEditor({ value, onChange, readOnly, ariaLabel }: CodeEditorProps) {
	return (
		<textarea
			aria-label={ariaLabel}
			readOnly={readOnly}
			value={value}
			onChange={(event) => onChange(event.target.value)}
		/>
	);
}
