import { Link } from "@tanstack/react-router";
import { ArrowLeft, ClipboardPenLine, ListPlus } from "lucide-react";
import { useState } from "react";
import type { CatalogEntryStatus } from "@/api/types.gen";
import { AreaVisualPicker } from "@/components/admin/practices/AreaVisualPicker";
import { PageHeader } from "@/components/core/PageHeader";
import { PageLayout } from "@/components/core/PageLayout";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button, buttonVariants } from "@/components/ui/button";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { canUseHephaestusVersion } from "./curated-entry-state";
import { HephaestusVersionPanel } from "./HephaestusVersionPanel";

export interface CuratedAreaFormValue {
	slug: string;
	name: string;
	description?: string;
	displayOrder: number;
	icon?: string;
	color?: string;
}

export interface CuratedAreaFormInitialValue extends CuratedAreaFormValue {
	status: CatalogEntryStatus;
	/** What Hephaestus ships now, when it differs — shown before anything is taken. */
	shipped?: Record<string, unknown> | null;
}

interface CuratedAreaFormBaseProps {
	isPending: boolean;
	conflict?: boolean;
	onContinueWithDraft?: () => void;
	isResetPending?: boolean;
	onUseHephaestusVersion?: () => void;
	onKeepOurs?: () => void;
	onSubmit: (value: CuratedAreaFormValue) => void;
}

export type CuratedAreaFormProps = CuratedAreaFormBaseProps &
	(
		| { mode: "create"; initialData?: never }
		| { mode: "edit"; initialData: CuratedAreaFormInitialValue }
	);

const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export function CuratedAreaForm(props: CuratedAreaFormProps) {
	const {
		mode,
		isPending,
		conflict,
		onContinueWithDraft,
		isResetPending = false,
		onUseHephaestusVersion,
		initialData,
		onKeepOurs,
		onSubmit,
	} = props;
	const [resetOpen, setResetOpen] = useState(false);
	const [slug, setSlug] = useState(initialData?.slug ?? "");
	const [name, setName] = useState(initialData?.name ?? "");
	const [description, setDescription] = useState(initialData?.description ?? "");
	const [displayOrder, setDisplayOrder] = useState(String(initialData?.displayOrder ?? 0));
	const [icon, setIcon] = useState<string | null>(initialData?.icon ?? null);
	const [color, setColor] = useState<string | null>(initialData?.color ?? null);
	const [submitted, setSubmitted] = useState(false);

	const slugError =
		mode === "create" && submitted && !SLUG_PATTERN.test(slug)
			? "Use lowercase letters, numbers and single hyphens."
			: undefined;
	const nameError = submitted && name.trim().length < 3 ? "Give the area a name." : undefined;
	const orderError =
		submitted && (!/^\d+$/.test(displayOrder) || Number(displayOrder) < 0)
			? "Use a whole number, zero or greater."
			: undefined;

	const submit = (event: React.FormEvent) => {
		event.preventDefault();
		setSubmitted(true);
		if (
			(mode === "create" && !SLUG_PATTERN.test(slug)) ||
			name.trim().length < 3 ||
			!/^\d+$/.test(displayOrder)
		) {
			return;
		}
		onSubmit({
			slug,
			name: name.trim(),
			description: description.trim() || undefined,
			displayOrder: Number(displayOrder),
			icon: icon ?? undefined,
			color: color ?? undefined,
		});
	};

	return (
		<PageLayout>
			<AlertDialog open={resetOpen} onOpenChange={setResetOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Use the Hephaestus version?</AlertDialogTitle>
						<AlertDialogDescription>
							This replaces your version of the area, and any unsaved edits, with the one Hephaestus
							ships. Whether the area is offered, and every workspace copy of it, stay as they are.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel disabled={isResetPending}>Keep our version</AlertDialogCancel>
						<AlertDialogAction
							disabled={isResetPending}
							onClick={() => {
								setResetOpen(false);
								onUseHephaestusVersion?.();
							}}
						>
							Use Hephaestus version
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>

			<Link
				from="/admin/catalog"
				to="/admin/catalog"
				search={(previous) => previous}
				className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "-ml-3 w-fit")}
			>
				<ArrowLeft className="size-4" aria-hidden />
				Practice catalog
			</Link>
			<PageHeader
				icon={mode === "create" ? <ListPlus /> : <ClipboardPenLine />}
				title={mode === "create" ? "Add an area" : `Edit: ${initialData.name}`}
				description={
					mode === "create"
						? "Areas group the practices a workspace receives."
						: "Saving replaces what this instance offers. Workspaces that already have it are unaffected."
				}
			/>

			{mode === "edit" && (
				<HephaestusVersionPanel
					status={initialData.status}
					kind="area"
					shipped={initialData.shipped}
					isResetPending={isResetPending}
					disabled={conflict ?? false}
					onUseHephaestusVersion={
						canUseHephaestusVersion(initialData.status) && onUseHephaestusVersion
							? () => setResetOpen(true)
							: undefined
					}
					onKeepOurs={onKeepOurs}
				/>
			)}

			{conflict && (
				<div className="max-w-3xl space-y-2">
					<Alert variant="warning">
						<AlertTitle>A newer version was saved while you were editing</AlertTitle>
						<AlertDescription>
							Your draft is unchanged. Continuing refreshes the version check; saving afterward
							replaces the latest definition with this entire draft.
						</AlertDescription>
					</Alert>
					{onContinueWithDraft && (
						<Button type="button" variant="outline" size="sm" onClick={onContinueWithDraft}>
							Continue with this draft
						</Button>
					)}
				</div>
			)}

			<form onSubmit={submit} className="max-w-3xl space-y-6" noValidate>
				{mode === "create" && (
					<Field data-invalid={slugError ? true : undefined}>
						<FieldLabel htmlFor="area-slug">Slug</FieldLabel>
						<Input
							id="area-slug"
							value={slug}
							onChange={(event) => setSlug(event.target.value)}
							aria-describedby="area-slug-description"
							aria-invalid={slugError ? true : undefined}
						/>
						<FieldDescription id="area-slug-description">
							The name this area keeps for life. Workspaces receive their copy under it.
						</FieldDescription>
						{slugError && <FieldError>{slugError}</FieldError>}
					</Field>
				)}

				<Field data-invalid={nameError ? true : undefined}>
					<FieldLabel htmlFor="area-name">Name</FieldLabel>
					<Input
						id="area-name"
						value={name}
						onChange={(event) => setName(event.target.value)}
						aria-invalid={nameError ? true : undefined}
					/>
					{nameError && <FieldError>{nameError}</FieldError>}
				</Field>

				<Field>
					<FieldLabel htmlFor="area-description">Description</FieldLabel>
					<Textarea
						id="area-description"
						value={description}
						rows={3}
						onChange={(event) => setDescription(event.target.value)}
						aria-describedby="area-description-help"
					/>
					<FieldDescription id="area-description-help">
						What this area develops, in the words a developer would use.
					</FieldDescription>
				</Field>

				<Field data-invalid={orderError ? true : undefined}>
					<FieldLabel htmlFor="area-order">Display order</FieldLabel>
					<Input
						id="area-order"
						inputMode="numeric"
						value={displayOrder}
						onChange={(event) => setDisplayOrder(event.target.value)}
						aria-describedby="area-order-help"
						aria-invalid={orderError ? true : undefined}
					/>
					<FieldDescription id="area-order-help">
						Where the area sits in a new workspace. Workspaces can reorder their own afterwards.
					</FieldDescription>
					{orderError && <FieldError>{orderError}</FieldError>}
				</Field>

				<AreaVisualPicker
					slug={slug || (initialData?.slug ?? "")}
					name={name}
					icon={icon}
					color={color}
					onChange={(patch) => {
						if (patch.icon !== undefined) setIcon(patch.icon);
						if (patch.color !== undefined) setColor(patch.color);
					}}
					disabled={isPending || isResetPending}
				/>

				<div className="flex flex-wrap gap-2">
					<Button type="submit" disabled={isPending || conflict || isResetPending}>
						{mode === "create" ? "Add area" : "Save area"}
					</Button>
					<Link
						from="/admin/catalog"
						to="/admin/catalog"
						search={(previous) => previous}
						className={buttonVariants({ variant: "outline" })}
					>
						Cancel
					</Link>
				</div>
			</form>
		</PageLayout>
	);
}
