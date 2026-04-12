import { Link } from "@tanstack/react-router";
import { Plus } from "lucide-react";
import { useState } from "react";
import type { Practice } from "@/api/types.gen";
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
	isLoading: boolean;
	isDeleting: boolean;
	togglingPractices: Set<string>;
	onDeletePractice: (slug: string) => Promise<void>;
	onSetActive: (slug: string, active: boolean) => void;
}

export function AdminPracticesPage({
	workspaceSlug,
	practices,
	isLoading,
	isDeleting,
	togglingPractices,
	onDeletePractice,
	onSetActive,
}: AdminPracticesPageProps) {
	const [deletingPractice, setDeletingPractice] = useState<Practice | null>(null);

	const handleDeleteConfirm = () => {
		if (deletingPractice) {
			onDeletePractice(deletingPractice.slug)
				.then(() => setDeletingPractice(null))
				.catch(() => {});
		}
	};

	return (
		<div className="container mx-auto max-w-3xl py-6">
			<div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-6">
				<div>
					<h1 className="text-3xl font-bold tracking-tight">Manage Practices</h1>
					<p className="text-muted-foreground">
						Configure practice definitions for evaluating developer contributions.
					</p>
				</div>
				<Button
					render={<Link to="/w/$workspaceSlug/admin/practices/new" params={{ workspaceSlug }} />}
				>
					<Plus className="mr-2 h-4 w-4" />
					Create Practice
				</Button>
			</div>

			<PracticeCardList
				workspaceSlug={workspaceSlug}
				practices={practices}
				isLoading={isLoading}
				togglingPractices={togglingPractices}
				onDelete={setDeletingPractice}
				onSetActive={onSetActive}
			/>

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
