import type { UserTeams as ApiUserTeams } from "@/api/types.gen";

// Extended interface for UserTeams that includes a user property for the components
export interface ExtendedUserTeams extends ApiUserTeams {
	user: {
		id: string | number;
		name: string;
		email: string;
		role?: string;
	};
}

// TypeScript utility to convert between API and component types
export const adaptApiUserTeams = (
	apiUserTeams: ApiUserTeams,
): ExtendedUserTeams => {
	return {
		...apiUserTeams,
		user: {
			id: apiUserTeams.id,
			name: apiUserTeams.name,
			email: apiUserTeams.login, // Use login as email if not provided
		},
	};
};
