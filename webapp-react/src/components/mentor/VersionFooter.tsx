import { motion } from "framer-motion";
import { useWindowSize } from "usehooks-ts";

import type { Document } from "@/api/types.gen";

import { Button } from "@/components/ui/button";
import { LoaderIcon } from "./LoaderIcon";

interface VersionFooterProps {
	/** Handler for version navigation */
	handleVersionChange: (type: "next" | "prev" | "toggle" | "latest") => void;
	/** Array of document versions */
	documents: Array<Document> | undefined;
	/** Current version index being viewed */
	currentVersionIndex: number;
	/** Whether restore operation is in progress */
	isRestoring?: boolean;
	/** Handler for restore action */
	onRestore?: () => void;
	/** Optional CSS class name */
	className?: string;
}

export const VersionFooter = ({
	handleVersionChange,
	documents,
	currentVersionIndex: _currentVersionIndex,
	isRestoring = false,
	onRestore,
	className,
}: VersionFooterProps) => {
	const { width } = useWindowSize();
	const isMobile = width < 768;

	if (!documents) return null;

	return (
		<motion.div
			className={`absolute flex flex-col gap-4 lg:flex-row bottom-0 bg-background p-4 w-full border-t z-50 justify-between ${className || ""}`}
			initial={{ y: isMobile ? 200 : 77 }}
			animate={{ y: 0 }}
			exit={{ y: isMobile ? 200 : 77 }}
			transition={{ type: "spring", stiffness: 140, damping: 20 }}
		>
			<div>
				<div>You are viewing a previous version</div>
				<div className="text-muted-foreground text-sm">
					Restore this version to make edits
				</div>
			</div>

			<div className="flex flex-row gap-4">
				<Button disabled={isRestoring} onClick={onRestore}>
					<div>Restore this version</div>
					{isRestoring && (
						<div className="animate-spin">
							<LoaderIcon />
						</div>
					)}
				</Button>
				<Button variant="outline" onClick={() => handleVersionChange("latest")}>
					Back to latest version
				</Button>
			</div>
		</motion.div>
	);
};
