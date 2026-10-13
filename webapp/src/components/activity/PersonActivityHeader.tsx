import { InlineLink } from "@/components/common/InlineLink";
import {
	DetailDrawerHeader,
	LevelPathRow,
} from "@/components/layout/detail-drawer/DetailDrawerHeader";
import type { LevelPath } from "@/components/layout/detail-drawer/DetailPath";
import { DrawerDescription, DrawerTitle } from "@/components/ui/drawer";
import { Skeleton } from "@/components/ui/skeleton";
import { getProviderTerms, type ProviderType } from "@/lib/provider/provider-terms";
import { hasText } from "@/lib/text";

import { activityPersonName, type ActivityPersonIdentity } from "./activity-people-rows";
import { MemberAvatar } from "./MemberAvatar";

/** The available profile identity, shared by public and member person levels. */
export function PersonActivityHeader({
	nested,
	path,
	login,
	person,
	loading = false,
	providerType,
	scope,
}: {
	nested?: boolean;
	path: LevelPath;
	login: string;
	person?: ActivityPersonIdentity;
	loading?: boolean;
	providerType: ProviderType;
	scope?: string;
}) {
	const name = activityPersonName(person, login);
	const profile = `${login} on ${getProviderTerms(providerType).displayName}`;
	return (
		<DetailDrawerHeader nested={nested} dismiss="inPath">
			<div className="flex min-w-0 flex-1 flex-col gap-3">
				<LevelPathRow {...path} current={name} />
				<div className="flex min-w-0 items-start gap-3">
					{person === undefined ? (
						loading && <Skeleton className="size-10 shrink-0 rounded-full" />
					) : (
						<MemberAvatar user={person} size="lg" />
					)}
					<div className="min-w-0 space-y-1">
						<DrawerTitle
							title={name}
							className="line-clamp-3 min-w-0 text-2xl font-semibold tracking-tight break-words"
						>
							{loading && person === undefined ? (
								<>
									<span className="sr-only">{login}</span>
									<Skeleton className="h-8 w-40 max-w-full" />
								</>
							) : (
								name
							)}
						</DrawerTitle>
						<DrawerDescription render={<div />}>
							{person !== undefined && hasText(person.htmlUrl) ? (
								<InlineLink href={person.htmlUrl} external>
									{profile}
								</InlineLink>
							) : (
								profile
							)}
							{scope !== undefined && <p>{scope}</p>}
						</DrawerDescription>
					</div>
				</div>
			</div>
		</DetailDrawerHeader>
	);
}
