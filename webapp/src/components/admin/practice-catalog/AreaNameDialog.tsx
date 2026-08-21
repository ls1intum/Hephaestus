import type { PracticeArea } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";

export interface AreaNameDialogProps {
	/** The area being renamed, or `null` when the dialog is naming a new one. */
	area: PracticeArea | null;
	open: boolean;
	pending: boolean;
	onOpenChange: (open: boolean) => void;
	/** Resolve `true` to close the dialog; `false` leaves it open with the typed name intact. */
	onSubmit: (name: string) => Promise<boolean>;
}

/**
 * Naming a practice area, whether it exists yet or not.
 *
 * These were two surfaces for one task: creating an area happened in a `Popover` and renaming one in
 * a `Dialog`, side by side on the same page. Same weight of decision, same single field, two
 * containers — the kind of difference a reader notices without being able to say why the page feels
 * untidy.
 *
 * Whether it is creating or renaming is read off `area` rather than carried in a second `mode` prop,
 * so the two can never disagree.
 *
 * No unsaved-changes guard, deliberately. A name is a few seconds to retype, which is the line the
 * drawer rule in `webapp/AGENTS.md` draws: a surface whose dismissal would need permission is a
 * route, and this is not one.
 */
export function AreaNameDialog({
	area,
	open,
	pending,
	onOpenChange,
	onSubmit,
}: AreaNameDialogProps) {
	const renaming = area !== null;

	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-sm">
				<DialogHeader>
					<DialogTitle>{renaming ? "Rename area" : "Create area"}</DialogTitle>
				</DialogHeader>
				<form
					onSubmit={async (event) => {
						event.preventDefault();
						// `namedItem` answers with a RadioNodeList when a name is shared, so narrow rather
						// than cast.
						const input = event.currentTarget.elements.namedItem("areaName");
						if (!(input instanceof HTMLInputElement)) return;
						const name = input.value.trim();
						// An empty name is nothing to do in either mode, but it must not look like a
						// decision: closing on it would discard a create the reader had started. Renaming
						// to the same name *is* a decision, and the decision is "never mind".
						if (!name) return;
						if (name === area?.name) {
							onOpenChange(false);
							return;
						}
						if (await onSubmit(name)) onOpenChange(false);
					}}
					className="space-y-4"
				>
					<Input
						name="areaName"
						required
						defaultValue={area?.name ?? ""}
						placeholder={renaming ? undefined : "New area name…"}
						aria-label={renaming ? "Area name" : "New area name"}
						autoComplete="off"
						disabled={pending}
					/>
					<DialogFooter>
						<Button
							type="button"
							variant="outline"
							onClick={() => onOpenChange(false)}
							disabled={pending}
						>
							Cancel
						</Button>
						<Button type="submit" className="min-w-20" disabled={pending}>
							{pending ? (renaming ? "Saving…" : "Creating…") : renaming ? "Save" : "Create"}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
}
