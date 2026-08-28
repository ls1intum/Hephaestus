import { RemovableToken } from "@/components/common/RemovableToken";

export type ReferenceFilterPillProps = {
	label: string;
	onClear: () => void;
} & ({ id: number; name?: string; value?: never } | { value: string; id?: never; name?: never });

export function ReferenceFilterPill({ label, id, name, value, onClear }: ReferenceFilterPillProps) {
	const shown = value ?? name ?? `#${id}`;
	return (
		<RemovableToken
			label={`${label}: ${shown}`}
			removeLabel={`Clear ${label.toLowerCase()} filter (${shown})`}
			onRemove={onClear}
		/>
	);
}
