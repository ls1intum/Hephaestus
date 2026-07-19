import { Button } from "@/components/ui/button";

/**
 * A value that filters the log when activated (e.g. an actor name). A real `<Button variant="link">` so
 * it carries the system's focus/hover/keyboard behaviour; the label is truncated in an inner block span
 * because `text-overflow` does not apply to the button's flex box.
 */
export function FilterLink({
	label,
	title,
	onSelect,
}: {
	label: string;
	title?: string;
	onSelect: () => void;
}) {
	return (
		<Button
			type="button"
			variant="link"
			title={title}
			onClick={onSelect}
			className="h-auto min-w-0 max-w-full justify-start p-0 font-normal"
		>
			<span className="truncate">{label}</span>
		</Button>
	);
}
