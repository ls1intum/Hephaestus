import { AlertCircle, Users } from "lucide-react";
import { type Contributor, ContributorGrid } from "@/components/shared/ContributorGrid";

interface ContributorSectionProps {
	contributors: Contributor[];
	isLoading: boolean;
	isError: boolean;
}

export function ContributorSection({ contributors, isLoading, isError }: ContributorSectionProps) {
	return (
		<div className="space-y-6">
			<div className="flex items-center gap-2 mb-4">
				<Users className="h-5 w-5 text-primary" />
				<h3 className="text-xl font-bold">Contributors</h3>
			</div>
			<p className="text-muted-foreground mb-8">
				These talented individuals have contributed their skills to help shape Hephaestus into what
				it is today. Each contributor brings unique expertise that strengthens our platform.
			</p>

			<ContributorGrid
				contributors={contributors}
				isLoading={isLoading}
				layout="comfortable"
				size="md"
			/>

			{isError && (
				<div className="bg-gradient-to-br from-background to-muted/30 rounded-lg p-8 text-center border border-muted">
					<AlertCircle className="h-8 w-8 text-destructive mx-auto mb-4" />
					<h4 className="text-lg font-medium mb-2">Contributor Data Unavailable</h4>
					<p className="text-muted-foreground">
						We're having trouble reaching our contributor information. Our team is working on
						it—please check back soon!
					</p>
				</div>
			)}
		</div>
	);
}
