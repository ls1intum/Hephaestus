import { ArrowDown, ArrowUp, Check, Plus, Trash2, X } from "lucide-react";
import { useState } from "react";
import type { Practice, PracticeArea } from "@/api/types.gen";
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";

interface PracticeAreasPanelProps {
	areas: PracticeArea[];
	practices: Practice[];
	onCreate: (name: string) => void;
	onRename: (slug: string, name: string) => void;
	onToggleActive: (slug: string, active: boolean) => void;
	onDelete: (slug: string) => void;
	/** Persist a new top-to-bottom ordering of all area slugs. */
	onReorder: (orderedSlugs: string[]) => void;
	isMutating: boolean;
}

/**
 * Presentational CRUD surface for practice areas: create, rename inline, toggle active, and delete.
 * An area is just a grouping — deleting it unbinds its practices (they keep their definitions), which
 * the delete confirmation states plainly.
 */
export function PracticeAreasPanel({
	areas,
	practices,
	onCreate,
	onRename,
	onToggleActive,
	onDelete,
	onReorder,
	isMutating,
}: PracticeAreasPanelProps) {
	const [newName, setNewName] = useState("");
	const [editingSlug, setEditingSlug] = useState<string | null>(null);
	const [editDraft, setEditDraft] = useState("");

	const countFor = (slug: string) => practices.filter((p) => p.areaSlug === slug).length;

	/** Swap the area at `index` with its neighbour and persist the whole new ordering. */
	const move = (index: number, direction: -1 | 1) => {
		const target = index + direction;
		if (target < 0 || target >= areas.length) return;
		const slugs = areas.map((g) => g.slug);
		[slugs[index], slugs[target]] = [slugs[target], slugs[index]];
		onReorder(slugs);
	};

	const submitNew = () => {
		const name = newName.trim();
		if (name.length < 3) return;
		onCreate(name);
		setNewName("");
	};

	const commitRename = (slug: string) => {
		const name = editDraft.trim();
		if (name.length >= 3) onRename(slug, name);
		setEditingSlug(null);
	};

	return (
		<div className="space-y-6">
			<div>
				<h2 className="text-lg font-semibold">Practice Areas</h2>
				<p className="text-sm text-muted-foreground">
					Practice areas group related practices into a learning objective. Create your own or
					adjust the seeded defaults — practices are bound to a practice area from the practice
					editor.
				</p>
			</div>

			{/* Add a practice area */}
			<div className="flex items-center gap-2">
				<Input
					placeholder="New practice area name, e.g. Packaging work for review"
					value={newName}
					onChange={(e) => setNewName(e.target.value)}
					onKeyDown={(e) => {
						if (e.key === "Enter") submitNew();
					}}
					aria-label="New practice area name"
				/>
				<Button onClick={submitNew} disabled={newName.trim().length < 3 || isMutating}>
					<Plus className="h-4 w-4" />
					Add practice area
				</Button>
			</div>

			{/* Areas list */}
			<ul className="divide-y rounded-lg border">
				{areas.length === 0 && (
					<li className="px-4 py-6 text-sm text-muted-foreground">
						No practice areas yet. Add one above to start grouping practices.
					</li>
				)}
				{areas.map((area, index) => (
					<li key={area.slug} className="flex items-center gap-3 px-4 py-3">
						{/* Reorder controls — drive the seeded catalog grouping order on the dashboards. */}
						<div className="flex flex-col">
							<Button
								size="icon-sm"
								variant="ghost"
								className="h-5 w-5"
								onClick={() => move(index, -1)}
								disabled={index === 0 || isMutating}
								aria-label={`Move ${area.name} up`}
							>
								<ArrowUp className="h-3.5 w-3.5" />
							</Button>
							<Button
								size="icon-sm"
								variant="ghost"
								className="h-5 w-5"
								onClick={() => move(index, 1)}
								disabled={index === areas.length - 1 || isMutating}
								aria-label={`Move ${area.name} down`}
							>
								<ArrowDown className="h-3.5 w-3.5" />
							</Button>
						</div>
						<div className="flex-1 min-w-0">
							{editingSlug === area.slug ? (
								<div className="flex items-center gap-1.5">
									<Input
										value={editDraft}
										onChange={(e) => setEditDraft(e.target.value)}
										onKeyDown={(e) => {
											if (e.key === "Enter") commitRename(area.slug);
											if (e.key === "Escape") setEditingSlug(null);
										}}
										className="h-8"
										autoFocus
										aria-label={`Rename ${area.name}`}
									/>
									<Button
										size="icon-sm"
										variant="ghost"
										onClick={() => commitRename(area.slug)}
										aria-label="Save name"
									>
										<Check className="h-4 w-4" />
									</Button>
									<Button
										size="icon-sm"
										variant="ghost"
										onClick={() => setEditingSlug(null)}
										aria-label="Cancel rename"
									>
										<X className="h-4 w-4" />
									</Button>
								</div>
							) : (
								<div className="flex items-center gap-2">
									<button
										type="button"
										className="font-medium truncate hover:underline"
										onClick={() => {
											setEditingSlug(area.slug);
											setEditDraft(area.name);
										}}
									>
										{area.name}
									</button>
									<Badge variant="secondary">{countFor(area.slug)} practices</Badge>
									{!area.active && <Badge variant="outline">Inactive</Badge>}
								</div>
							)}
							<p className="text-xs text-muted-foreground truncate">{area.slug}</p>
						</div>

						<Switch
							checked={area.active}
							onCheckedChange={(checked) => onToggleActive(area.slug, checked)}
							disabled={isMutating}
							aria-label={`${area.active ? "Deactivate" : "Activate"} ${area.name}`}
						/>

						<AlertDialog>
							<AlertDialogTrigger
								render={
									<Button size="icon-sm" variant="ghost" aria-label={`Delete ${area.name}`}>
										<Trash2 className="h-4 w-4 text-destructive" />
									</Button>
								}
							/>
							<AlertDialogContent>
								<AlertDialogHeader>
									<AlertDialogTitle>Delete “{area.name}”?</AlertDialogTitle>
									<AlertDialogDescription>
										The {countFor(area.slug)} practices bound to this practice area keep their
										definitions — they just become unassigned. This cannot be undone.
									</AlertDialogDescription>
								</AlertDialogHeader>
								<AlertDialogFooter>
									<AlertDialogCancel>Cancel</AlertDialogCancel>
									<AlertDialogAction onClick={() => onDelete(area.slug)}>
										Delete practice area
									</AlertDialogAction>
								</AlertDialogFooter>
							</AlertDialogContent>
						</AlertDialog>
					</li>
				))}
			</ul>
		</div>
	);
}
