export interface EditorProps {
	value?: string;
	onChange?: (value: string) => void;
	options?: {
		ariaLabel?: string;
		readOnly?: boolean;
	};
}

export default function Editor({ value, onChange, options }: EditorProps) {
	return (
		<textarea
			aria-label={options?.ariaLabel}
			readOnly={options?.readOnly}
			value={value}
			onChange={(event) => onChange?.(event.target.value)}
		/>
	);
}
