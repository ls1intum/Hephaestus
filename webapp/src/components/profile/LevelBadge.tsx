interface LevelBadgeProps {
	level: number;
}

export function LevelBadge({ level }: LevelBadgeProps) {
	return (
		<div className="flex flex-col items-center justify-center min-w-[4.5rem] p-3 rounded-xl bg-secondary/50 border border-border/50 backdrop-blur-sm shadow-sm">
			<span className="text-[0.65rem] uppercase text-muted-foreground font-bold tracking-wider">
				LEVEL
			</span>
			<span className="text-3xl font-black text-foreground leading-none tracking-tight">
				{level}
			</span>
		</div>
	);
}
