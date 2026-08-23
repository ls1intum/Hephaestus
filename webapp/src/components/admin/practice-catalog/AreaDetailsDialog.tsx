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
import { Field, FieldDescription, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { AreaVisualPicker } from "./AreaVisualPicker";
import { generateSlug } from "./constants";

export interface AreaDetails {
	name: string;
	icon: string | null;
	color: string | null;
}

export interface AreaDetailsDialogProps {
	area: PracticeArea | null;
	open: boolean;
	pending: boolean;
	onOpenChange: (open: boolean) => void;
	/** Resolve `true` to close; `false` leaves the dialog open with the typed details intact. */
	onSubmit: (details: AreaDetails) => Promise<boolean>;
}

/**
 * Naming a practice area and giving it its chip, whether it exists yet or not.
 *
 * Which of the two it is comes from `area` rather than a second `mode` prop, so the title, the
 * button and the request cannot disagree.
 *
 * No unsaved-changes guard: this is seconds to retype, which is the line the drawer rule in
 * `webapp/AGENTS.md` draws between a dismissible surface and a route.
 */
export function AreaDetailsDialog({
	area,
	open,
	pending,
	onOpenChange,
	onSubmit,
}: AreaDetailsDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-sm">
				{/* Reopening inside the close animation reuses the still-mounted child, so the key is
				    what actually resets the fields to this area's values. */}
				<AreaDetailsForm
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

function AreaDetailsForm({
	area,
	pending,
	onOpenChange,
	onSubmit,
}: Omit<AreaDetailsDialogProps, "open">) {
	const fieldId = useId();
	const helpId = useId();
	const editing = area !== null;
	const [name, setName] = useState(area?.name ?? "");
	const [icon, setIcon] = useState<string | null>(area?.icon ?? null);
	const [color, setColor] = useState<string | null>(area?.color ?? null);
	const trimmed = name.trim();
	const unchanged =
		trimmed === area?.name && icon === (area?.icon ?? null) && color === (area?.color ?? null);

	return (
		<DialogForm
			onSubmit={async (event) => {
				event.preventDefault();
				// Re-submitting untouched details is a deliberate "never mind".
				if (unchanged) return onOpenChange(false);
				if (await onSubmit({ name: trimmed, icon, color })) onOpenChange(false);
			}}
		>
			<DialogHeader>
				<DialogTitle>{editing ? "Edit group" : "Create group"}</DialogTitle>
			</DialogHeader>
			<DialogBody className="py-1">
				<FieldGroup>
					<Field>
						<FieldLabel htmlFor={fieldId}>Name</FieldLabel>
						<div className="flex items-center gap-2">
							{/* The picker seeds from the slug, which a new area does not have until the name
							    is typed — deriving it here is what makes the default chip track what you type. */}
							<AreaVisualPicker
								describedBy={helpId}
								slug={area?.slug ?? generateSlug(trimmed)}
								name={trimmed}
								icon={icon}
								color={color}
								onChange={(patch) => {
									if (patch.icon !== undefined) setIcon(patch.icon);
									if (patch.color !== undefined) setColor(patch.color);
								}}
								disabled={pending}
							/>
							<Input
								id={fieldId}
								value={name}
								onChange={(event) => setName(event.target.value)}
								placeholder={editing ? undefined : "New group name…"}
								autoComplete="off"
								disabled={pending}
								aria-describedby={helpId}
								autoFocus
							/>
						</div>
						<FieldDescription id={helpId}>
							The icon and colour appear on this group's chip. Left alone, both follow the name.
						</FieldDescription>
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
					{pending ? (editing ? "Saving…" : "Creating…") : editing ? "Save" : "Create"}
				</Button>
			</DialogFooter>
		</DialogForm>
	);
}
