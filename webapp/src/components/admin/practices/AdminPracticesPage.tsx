import { Link } from "@tanstack/react-router";
import { AlertCircle, Plus } from "lucide-react";
import { useState } from "react";
import type { Practice, PracticeArea } from "@/api/types.gen";
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
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { PracticeCardList } from "./PracticeCardList";

interface AdminPracticesPageProps {
	workspaceSlug: string;
	practices: Practice[];
	goals: PracticeArea[];
	isLoading: boolean;
	isError?: boolean;
	isDeleting: boolean;
	togglingPractices: Set<string>;
	onDeletePractice: (slug: string) => Promise<void>;
	onSetActive: (slug: string, active: boolean) => void;
	onRetry?: () => void;
}

type FocusFilter = "ALL" | "PULL_REQUEST" | "ISSUE";
const FOCUS_FILTERS: ReadonlyArray<{ value: FocusFilter; label: string }> = [
	{ value: "ALL", label: "All" },
	{ value: "PULL_REQUEST", label: "Pull requests" },
	{ value: "ISSUE", label: "Issues" },
];

export function AdminPracticesPage({
	workspaceSlug,
	practices,
	goals,
	isLoading,
	isError = false,
	isDeleting,
	togglingPractices,
	onDeletePractice,
	onSetActive,
	onRetry,
}: AdminPracticesPageProps) {
	const [deletingPractice, setDeletingPractice] = useState<Practice | null>(null);
	const [focusFilter, setFocusFilter] = useState<FocusFilter>("ALL");

	const visiblePractices =
		focusFilter === "ALL" ? practices : practices.filter((p) => p.artifactType === focusFilter);

	const handleDeleteConfirm = () => {
		if (deletingPractice) {
			onDeletePractice(deletingPractice.slug)
				.then(() => setDeletingPractice(null))
				.catch(() => {});
		}
	};

	return (
		<div>
			<div className="mb-6 flex items-center justify-between gap-3">
				<div
					className="inline-flex rounded-lg border p-0.5"
					role="group"
					aria-label="Filter by focus"
				>
					{FOCUS_FILTERS.map((filter) => (
						<Button
							key={filter.value}
							type="button"
							size="sm"
							variant={focusFilter === filter.value ? "secondary" : "ghost"}
							aria-pressed={focusFilter === filter.value}
							onClick={() => setFocusFilter(filter.value)}
						>
							{filter.label}
						</Button>
					))}
				</div>
				<Button
					render={
						<Link
							to="/w/$workspaceSlug/admin/ai/practice-detection/catalog/new"
							params={{ workspaceSlug }}
						/>
					}
				>
					<Plus className="mr-2 h-4 w-4" />
					Create Practice
				</Button>
			</div>

			{isError ? (
				<Alert variant="destructive">
					<AlertCircle />
					<AlertTitle>Failed to load practices</AlertTitle>
					<AlertDescription>
						<p>The practice catalog could not be loaded.</p>
						{onRetry && (
							<Button variant="outline" size="sm" className="mt-2" onClick={onRetry}>
								Retry
							</Button>
						)}
					</AlertDescription>
				</Alert>
			) : (
				<PracticeCardList
					workspaceSlug={workspaceSlug}
					practices={visiblePractices}
					goals={goals}
					isLoading={isLoading}
					togglingPractices={togglingPractices}
					onDelete={setDeletingPractice}
					onSetActive={onSetActive}
				/>
			)}

			<AlertDialog
				open={deletingPractice !== null}
				onOpenChange={(open) => {
					if (!open) setDeletingPractice(null);
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>Delete &ldquo;{deletingPractice?.name}&rdquo;?</AlertDialogTitle>
						<AlertDialogDescription>
							This will permanently delete this practice definition and all associated findings.
							This action cannot be undone.
						</AlertDialogDescription>
					</AlertDialogHeader>
					<AlertDialogFooter>
						<AlertDialogCancel>Cancel</AlertDialogCancel>
						<AlertDialogAction
							onClick={handleDeleteConfirm}
							disabled={isDeleting}
							className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
						>
							{isDeleting ? (
								<>
									<Spinner className="mr-2 h-4 w-4" />
									Deleting...
								</>
							) : (
								"Delete Practice"
							)}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
}
