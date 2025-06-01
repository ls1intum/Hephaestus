// League data structure matches the structure in the Angular application
export const Leagues = [
	{
		name: "Bronze",
		minPoints: 0,
		maxPoints: 1250,
	},
	{
		name: "Silver",
		minPoints: 1250,
		maxPoints: 1500,
	},
	{
		name: "Gold",
		minPoints: 1500,
		maxPoints: 1750,
	},
	{
		name: "Diamond",
		minPoints: 1750,
		maxPoints: 2000,
	},
	{
		name: "Master",
		minPoints: 2000,
		maxPoints: Number.POSITIVE_INFINITY,
	},
];

/**
 * Returns the league tier based on league points
 */
export function getLeagueFromPoints(points: number) {
	return Leagues.find(
		(league) => points >= league.minPoints && points < league.maxPoints,
	);
}

/**
 * Returns the league tier name (lowercase) based on league points
 * Used for CSS class names
 */
export function getLeagueTier(
	points?: number,
): "bronze" | "silver" | "gold" | "diamond" | "master" | "none" {
	if (points === undefined) {
		return "none";
	}

	if (points < 1250) {
		return "bronze";
	}
	if (points < 1500) {
		return "silver";
	}
	if (points < 1750) {
		return "gold";
	}
	if (points < 2000) {
		return "diamond";
	}
	return "master";
}

/**
 * Returns the league tier label based on tier
 */
export function getLeagueLabel(tier: ReturnType<typeof getLeagueTier>): string {
	const labels = {
		none: "Not Ranked",
		bronze: "Bronze",
		silver: "Silver",
		gold: "Gold",
		diamond: "Diamond",
		master: "Master",
	};

	return labels[tier];
}
