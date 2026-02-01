import type { UserTeams as ApiUserTeams } from "@/api/types.gen";

// Extended interface for UserTeams that includes a user property for the components
export interface ExtendedUserTeams extends Omit<ApiUserTeams, "teams"> {
	teams: ApiUserTeams["teams"]; // Preserve the original teams array type
	user: {
		id: string | number;
		name: string;
		login: string;
		email?: string;
		role?: string;
	};
}

// TypeScript utility to convert between API and component types
export const adaptApiUserTeams = (apiUserTeams: ApiUserTeams): ExtendedUserTeams => {
	return {
		...apiUserTeams,
		user: {
			id: apiUserTeams.id,
			name: apiUserTeams.name,
			login: String(apiUserTeams.login ?? ""),
			email: apiUserTeams.email,
		},
	};
};
