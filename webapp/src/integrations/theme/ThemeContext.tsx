import { createContext, useContext, useEffect, useState } from "react";

const THEMES = ["dark", "light", "system"] as const;

type Theme = (typeof THEMES)[number];

/** The stored theme is user-writable, so anything unrecognised falls back to the default. */
function isTheme(value: string | null): value is Theme {
	return THEMES.some((theme) => theme === value);
}

type ThemeProviderProps = {
	children?: React.ReactNode;
	defaultTheme?: Theme;
	storageKey?: string;
};

type ThemeProviderState = {
	theme: Theme;
	setTheme: (theme: Theme) => void;
};

/**
 * What a consumer reads outside a `ThemeProvider`: the system theme, and a `setTheme` that does
 * nothing. Deliberately a fallback rather than a thrown invariant — theme-aware components are
 * rendered on their own in tests and in isolation, where the surrounding shell is not mounted.
 */
const NO_PROVIDER: ThemeProviderState = {
	theme: "system",
	setTheme: () => {},
};

const ThemeProviderContext = createContext<ThemeProviderState>(NO_PROVIDER);

export function ThemeProvider({
	children,
	defaultTheme = "system",
	storageKey = "theme",
	...props
}: ThemeProviderProps) {
	const [theme, setTheme] = useState<Theme>(() => {
		const stored = localStorage.getItem(storageKey);
		return isTheme(stored) ? stored : defaultTheme;
	});

	useEffect(() => {
		const root = window.document.documentElement;

		root.classList.remove("light", "dark");

		let appliedTheme: "light" | "dark";
		if (theme === "system") {
			appliedTheme = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
		} else {
			appliedTheme = theme;
		}

		root.classList.add(appliedTheme);
		root.setAttribute("data-color-mode", appliedTheme);

		const metaThemeColor = document.querySelector('meta[name="theme-color"]');
		if (metaThemeColor) {
			const color = appliedTheme === "dark" ? "hsl(0 0% 10%)" : "hsl(0 0% 100%)";
			metaThemeColor.setAttribute("content", color);
		}
	}, [theme]);

	const contextValue: ThemeProviderState = {
		theme,
		setTheme: (next: Theme) => {
			localStorage.setItem(storageKey, next);
			setTheme(next);
		},
	};

	return (
		<ThemeProviderContext.Provider {...props} value={contextValue}>
			{children}
		</ThemeProviderContext.Provider>
	);
}

export const useTheme = () => useContext(ThemeProviderContext);
