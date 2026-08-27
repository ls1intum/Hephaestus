import { useId, useState } from "react";
import type { PracticeGroup } from "@/api/types.gen";
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
import { generateSlug } from "./constants";
import { GroupVisualPicker } from "./GroupVisualPicker";

export interface GroupDetails {
	name: string;
	icon: string | null;
	color: string | null;
}

export interface GroupDetailsDialogProps {
	group: PracticeGroup | null;
	open: boolean;
	pending: boolean;
	onOpenChange: (open: boolean) => void;
	/** Resolve `true` to close; `false` leaves the dialog open with the typed details intact. */
	onSubmit: (details: GroupDetails) => Promise<boolean>;
}

/**
 * Naming a practice group and giving it its chip, whether it exists yet or not.
 *
 * Which of the two it is comes from `group` rather than a second `mode` prop, so the title, the
 * button and the request cannot disagree.
 *
 * Unguarded, unlike the practice editors: a name, an icon and a colour are seconds to retype, so a
 * confirmation on dismissal would cost more than it saves.
 */
export function GroupDetailsDialog({
	group,
	open,
	pending,
	onOpenChange,
	onSubmit,
}: GroupDetailsDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className="sm:max-w-sm">
				{/* Reopening inside the close animation reuses the still-mounted child, so the key is
				    what actually resets the fields to this group's values. */}
				<GroupDetailsForm
					key={group?.slug ?? "new"}
					group={group}
					pending={pending}
					onOpenChange={onOpenChange}
					onSubmit={onSubmit}
				/>
			</DialogContent>
		</Dialog>
	);
}

function GroupDetailsForm({
	group,
	pending,
	onOpenChange,
	onSubmit,
}: Omit<GroupDetailsDialogProps, "open">) {
	const fieldId = useId();
	const helpId = useId();
	const editing = group !== null;
	const [name, setName] = useState(group?.name ?? "");
	const [icon, setIcon] = useState<string | null>(group?.icon ?? null);
	const [color, setColor] = useState<string | null>(group?.color ?? null);
	const trimmed = name.trim();
	const unchanged =
		trimmed === group?.name && icon === (group.icon ?? null) && color === (group.color ?? null);

	const saveDetails = async () => {
		if (unchanged) return onOpenChange(false);
		if (await onSubmit({ name: trimmed, icon, color })) onOpenChange(false);
	};

	return (
		<DialogForm
			onSubmit={(event) => {
				event.preventDefault();
				void saveDetails();
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
							<Input
								id={fieldId}
								value={name}
								onChange={(event) => setName(event.target.value)}
								placeholder={editing ? undefined : "New group name…"}
								autoComplete="off"
								disabled={pending}
								aria-describedby={helpId}
							/>
							<GroupVisualPicker
								describedBy={helpId}
								slug={group?.slug ?? generateSlug(trimmed)}
								name={trimmed}
								icon={icon}
								color={color}
								onChange={(patch) => {
									if (patch.icon !== undefined) setIcon(patch.icon);
									if (patch.color !== undefined) setColor(patch.color);
								}}
								disabled={pending}
							/>
						</div>
						<FieldDescription id={helpId}>
							The icon and color appear on this group's chip. Left alone, both follow the name.
						</FieldDescription>
					</Field>
				</FieldGroup>
			</DialogBody>
			<DialogFooter>
				<DialogClose render={<Button type="button" variant="outline" disabled={pending} />}>
					Cancel
				</DialogClose>
				<Button type="submit" className="min-w-20" disabled={pending || trimmed.length === 0}>
					{pending && <Spinner className="size-4" aria-hidden />}
					{pending ? (editing ? "Saving…" : "Creating…") : editing ? "Save" : "Create"}
				</Button>
			</DialogFooter>
		</DialogForm>
	);
}
