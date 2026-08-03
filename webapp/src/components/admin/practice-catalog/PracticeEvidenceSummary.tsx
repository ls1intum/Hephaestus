import type { PracticeEvidenceDeclaration, PracticeEvidenceValidation } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

const VALIDATION_LABELS: Record<PracticeEvidenceValidation["status"], string> = {
	AUTHOR_DECLARED: "Not independently validated",
	INDEPENDENTLY_VALIDATED: "Independently validated",
	STALE: "Validation is stale",
	SUPERSEDED: "Validation is superseded",
};

const VALIDATION_VARIANTS: Record<
	PracticeEvidenceValidation["status"],
	"outline" | "success" | "warning"
> = {
	AUTHOR_DECLARED: "outline",
	INDEPENDENTLY_VALIDATED: "success",
	STALE: "warning",
	SUPERSEDED: "warning",
};

const UNSATISFIED_LABELS: Record<PracticeEvidenceDeclaration["onUnsatisfied"], string> = {
	DECLINE_SEMANTIC_JUDGMENT: "Decline the semantic judgment",
};

function words(token: string): string {
	return token
		.replace(/_/g, " ")
		.toLowerCase()
		.replace(/^./, (letter) => letter.toUpperCase());
}

function Requirements({
	requirements,
}: {
	requirements: PracticeEvidenceDeclaration["required"] | PracticeEvidenceDeclaration["optional"];
}) {
	if (requirements.length === 0) return <span>None</span>;
	return (
		<ul className="space-y-1">
			{requirements.map((requirement) => (
				<li key={requirement.sourceKind}>
					<code className="break-all">{requirement.sourceKind}</code>
					<span className="text-muted-foreground">
						{" "}
						({words(requirement.completeness)}, {words(requirement.freshness)})
					</span>
				</li>
			))}
		</ul>
	);
}

export interface PracticeEvidenceSummaryProps {
	declaration: PracticeEvidenceDeclaration;
	validation: PracticeEvidenceValidation;
	className?: string;
}

export function PracticeEvidenceSummary({
	declaration,
	validation,
	className,
}: PracticeEvidenceSummaryProps) {
	return (
		<dl className={cn("grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2", className)}>
			<div>
				<dt className="font-medium">Contract</dt>
				<dd className="text-muted-foreground">
					{declaration.sourceContractVersion} · {declaration.profile}
				</dd>
			</div>
			<div>
				<dt className="font-medium">Author-declared observability</dt>
				<dd className="text-muted-foreground">{words(declaration.observability)}</dd>
			</div>
			<div>
				<dt className="font-medium">Required evidence</dt>
				<dd>
					<Requirements requirements={declaration.required} />
				</dd>
			</div>
			<div>
				<dt className="font-medium">Optional evidence</dt>
				<dd>
					<Requirements requirements={declaration.optional} />
				</dd>
			</div>
			<div>
				<dt className="font-medium">When requirements are not met</dt>
				<dd className="text-muted-foreground">{UNSATISFIED_LABELS[declaration.onUnsatisfied]}</dd>
			</div>
			<div>
				<dt className="font-medium">Independent validation</dt>
				<dd className="space-y-1">
					<Badge variant={VALIDATION_VARIANTS[validation.status]}>
						{VALIDATION_LABELS[validation.status]}
					</Badge>
					{validation.validator && (
						<p className="text-muted-foreground">
							{validation.validator}
							{validation.validationReference && (
								<>
									{" · "}
									<span className="break-all">{validation.validationReference}</span>
								</>
							)}
						</p>
					)}
					{validation.status !== "AUTHOR_DECLARED" && (
						<p className="text-muted-foreground">
							Contract {validation.sourceContractVersion} · declaration{" "}
							<code className="break-all">{validation.declarationDigest}</code> · validated{" "}
							<RelativeTime value={validation.validatedAt} fallback="at an unknown time" />
						</p>
					)}
				</dd>
			</div>
			{declaration.blindSpots.length > 0 && (
				<div className="sm:col-span-2">
					<dt className="font-medium">Declared blind spots</dt>
					<dd>
						<ul className="space-y-1">
							{declaration.blindSpots.map((blindSpot) => (
								<li key={blindSpot.code}>
									<code className="break-all">{blindSpot.code}</code>: {blindSpot.summary}
								</li>
							))}
						</ul>
					</dd>
				</div>
			)}
		</dl>
	);
}
