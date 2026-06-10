import { Link } from "@tanstack/react-router";
import { AlertCircle, Plus } from "lucide-react";
import { useState } from "react";
import type { Practice } from "@/api/types.gen";
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
	isLoading: boolean;
	isError?: boolean;
	isDeleting: boolean;
	togglingPractices: Set<string>;
	onDeletePractice: (slug: string) => Promise<void>;
	onSetActive: (slug: string, active: boolean) => void;
	onRetry?: () => void;
}

export function AdminPracticesPage({
	workspaceSlug,
	practices,
	isLoading,
	isError = false,
	isDeleting,
	togglingPractices,
	onDeletePractice,
	onSetActive,
	onRetry,
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
		<div>
			<div className="mb-6 flex justify-end">
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
					practices={practices}
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
