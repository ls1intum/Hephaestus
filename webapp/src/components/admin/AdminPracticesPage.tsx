import { Plus } from "lucide-react";
import { useState } from "react";
import type { CreatePracticeRequest, Practice, UpdatePracticeRequest } from "@/api/types.gen";
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
import { AdminPracticesTable } from "./AdminPracticesTable";
import { PracticeFormDialog } from "./PracticeFormDialog";

interface AdminPracticesPageProps {
	practices: Practice[];
	isLoading: boolean;
	isCreating: boolean;
	isUpdating: boolean;
	isDeleting: boolean;
	togglingPractices: Set<string>;
	onCreatePractice: (data: CreatePracticeRequest) => Promise<void>;
	onUpdatePractice: (slug: string, data: UpdatePracticeRequest) => Promise<void>;
	onDeletePractice: (slug: string) => Promise<void>;
	onSetActive: (slug: string, active: boolean) => void;
}

export function AdminPracticesPage({
	practices,
	isLoading,
	isCreating,
	isUpdating,
	isDeleting,
	togglingPractices,
	onCreatePractice,
	onUpdatePractice,
	onDeletePractice,
	onSetActive,
}: AdminPracticesPageProps) {
	const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
	const [editingPractice, setEditingPractice] = useState<Practice | null>(null);
	const [deletingPractice, setDeletingPractice] = useState<Practice | null>(null);

	const handleCreateSubmit = (data: CreatePracticeRequest) => {
		onCreatePractice(data)
			.then(() => setIsCreateDialogOpen(false))
			.catch(() => {});
	};

	const handleEditSubmit = (slug: string, data: UpdatePracticeRequest) => {
		onUpdatePractice(slug, data)
			.then(() => setEditingPractice(null))
			.catch(() => {});
	};

	const handleDeleteConfirm = () => {
		if (deletingPractice) {
			onDeletePractice(deletingPractice.slug)
				.then(() => setDeletingPractice(null))
				.catch(() => {});
		}
	};

	return (
		<div className="container mx-auto py-6">
			<div className="flex items-center justify-between mb-6">
				<div>
					<h1 className="text-3xl font-bold tracking-tight">Manage Practices</h1>
					<p className="text-muted-foreground">
						Configure practice definitions for evaluating developer contributions.
					</p>
				</div>
				<Button onClick={() => setIsCreateDialogOpen(true)}>
					<Plus className="mr-2 h-4 w-4" />
					Create Practice
				</Button>
			</div>

			<AdminPracticesTable
				practices={practices}
				isLoading={isLoading}
				togglingPractices={togglingPractices}
				onEdit={setEditingPractice}
				onDelete={setDeletingPractice}
				onSetActive={onSetActive}
				onCreateClick={() => setIsCreateDialogOpen(true)}
			/>

			{/* Create Dialog */}
			<PracticeFormDialog
				mode="create"
				open={isCreateDialogOpen}
				onOpenChange={setIsCreateDialogOpen}
				onSubmit={handleCreateSubmit}
				isPending={isCreating}
			/>

			{/* Edit Dialog */}
			{editingPractice && (
				<PracticeFormDialog
					mode="edit"
					open
					onOpenChange={(open) => {
						if (!open) setEditingPractice(null);
					}}
					onSubmit={handleEditSubmit}
					isPending={isUpdating}
					initialData={editingPractice}
				/>
			)}

			{/* Delete Confirmation */}
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
