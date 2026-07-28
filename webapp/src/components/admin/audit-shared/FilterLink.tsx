import { Button } from "@/components/ui/button";

/** The label truncates in an inner span because `text-overflow` does not apply to the button's own
 * flex box. */
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
