import { useId, useState } from "react";
import type { PracticeArea } from "@/api/types.gen";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogBody,
	DialogClose,
	DialogContent,
	DialogFooter,
	DialogForm,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";

export interface AreaNameDialogProps {
	/** The area being renamed, or `null` when the dialog is naming a new one. */
	area: PracticeArea | null;
	open: boolean;
	pending: boolean;
	onOpenChange: (open: boolean) => void;
	/** Resolve `true` to close; `false` leaves the dialog open with the typed name intact. */
	onSubmit: (name: string) => Promise<boolean>;
}

/**
 * Naming a practice area, whether it exists yet or not.
 *
 * Which of the two it is comes from `area` rather than a second `mode` prop, so the title, the
 * button and the request cannot disagree.
 *
 * No unsaved-changes guard: a name is seconds to retype, which is the line the drawer rule in
 * `webapp/AGENTS.md` draws between a dismissible surface and a route.
 */
export function AreaNameDialog({
	area,
	open,
	pending,
	onOpenChange,
	onSubmit,
}: AreaNameDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-sm">
				{/* Reopening inside the close animation reuses the still-mounted child, so the key is
				    what actually resets the field to this area's name. */}
				<AreaNameForm
					key={area?.slug ?? "new"}
					area={area}
					pending={pending}
					onOpenChange={onOpenChange}
					onSubmit={onSubmit}
				/>
			</DialogContent>
		</Dialog>
	);
}

function AreaNameForm({
	area,
	pending,
	onOpenChange,
	onSubmit,
}: Omit<AreaNameDialogProps, "open">) {
	const fieldId = useId();
	const renaming = area !== null;
	const [name, setName] = useState(area?.name ?? "");
	const trimmed = name.trim();

	return (
		<>
			<DialogForm
				onSubmit={async (event) => {
					event.preventDefault();
					// Re-submitting an unchanged name is a deliberate "never mind".
					if (trimmed === area?.name) return onOpenChange(false);
					if (await onSubmit(trimmed)) onOpenChange(false);
				}}
			>
				<DialogHeader>
					<DialogTitle>{renaming ? "Rename area" : "Create area"}</DialogTitle>
				</DialogHeader>
				<DialogBody className="py-1">
					<FieldGroup>
						<Field>
							<FieldLabel htmlFor={fieldId}>Name</FieldLabel>
							<Input
								id={fieldId}
								value={name}
								onChange={(event) => setName(event.target.value)}
								placeholder={renaming ? undefined : "New area name…"}
								autoComplete="off"
								disabled={pending}
								autoFocus
							/>
						</Field>
					</FieldGroup>
				</DialogBody>
				<DialogFooter>
					<DialogClose render={<Button type="button" variant="outline" disabled={pending} />}>
						Cancel
					</DialogClose>
					{/* Disabled rather than validated on submit: one required field needs no error message. */}
					<Button type="submit" className="min-w-20" disabled={pending || trimmed.length === 0}>
						{pending && <Spinner className="size-4" aria-hidden />}
						{pending ? (renaming ? "Saving…" : "Creating…") : renaming ? "Save" : "Create"}
					</Button>
				</DialogFooter>
			</DialogForm>
		</>
	);
}
