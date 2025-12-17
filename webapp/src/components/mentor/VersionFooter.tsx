import { motion } from "framer-motion";
import { useWindowSize } from "usehooks-ts";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

interface VersionFooterProps {
	/** Handler for version navigation */
	handleVersionChange: (type: "next" | "prev" | "toggle" | "latest") => void;
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
	currentVersionIndex: _currentVersionIndex,
	isRestoring = false,
	onRestore,
	className,
}: VersionFooterProps) => {
	const { width } = useWindowSize();
	const isMobile = width < 768;

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
					{isRestoring && <Spinner size="sm" />}
				</Button>
				<Button variant="outline" onClick={() => handleVersionChange("latest")}>
					Back to latest version
				</Button>
			</div>
		</motion.div>
	);
};
