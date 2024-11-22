import React, { createContext, useContext, useEffect, useState, ReactNode } from "react";

export enum AppTheme {
    LIGHT = "light",
    DARK = "dark",
    AUTO = "auto"
}

interface ThemeContextType {
    currentTheme: AppTheme | undefined;
    setLightTheme: () => void;
    setDarkTheme: () => void;
    setSystemTheme: () => void;
}

const LOCAL_STORAGE_THEME_KEY = "theme";

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const useTheme = (): ThemeContextType => {
    const context = useContext(ThemeContext);
    if (!context) {
        throw new Error("useTheme must be used within a ThemeProvider");
    }
    return context;
};

interface ThemeProviderProps {
    children: ReactNode;
}

export const ThemeProvider: React.FC<ThemeProviderProps> = ({ children }) => {
    const [currentTheme, setCurrentTheme] = useState<AppTheme | undefined>(
        getInitialTheme()
    );

    const htmlElement = document.documentElement;
    const metaThemeColor = document.querySelector<HTMLMetaElement>(
        'meta[name="theme-color"]'
    );

    useEffect(() => {
        if (currentTheme === AppTheme.AUTO) {
            applySystemTheme();
            const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
            const handleChange = (event: MediaQueryListEvent) => {
                if (currentTheme === AppTheme.AUTO) {
                    htmlElement.classList.toggle(AppTheme.DARK, event.matches);
                    updateMetaThemeColor();
                }
            };
            mediaQuery.addEventListener("change", handleChange);
            return () => {
                mediaQuery.removeEventListener("change", handleChange);
            };
        } else {
            applyTheme(currentTheme ?? AppTheme.LIGHT);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [currentTheme]);

    const setLightTheme = () => {
        applyTheme(AppTheme.LIGHT);
        setCurrentTheme(AppTheme.LIGHT);
        localStorage.setItem(LOCAL_STORAGE_THEME_KEY, AppTheme.LIGHT);
    };

    const setDarkTheme = () => {
        applyTheme(AppTheme.DARK);
        setCurrentTheme(AppTheme.DARK);
        localStorage.setItem(LOCAL_STORAGE_THEME_KEY, AppTheme.DARK);
    };

    const setSystemTheme = () => {
        setCurrentTheme(AppTheme.AUTO);
        localStorage.removeItem(LOCAL_STORAGE_THEME_KEY);
        applySystemTheme();
        updateMetaThemeColor();
    };

    const applyTheme = (theme: AppTheme) => {
        htmlElement.classList.toggle(AppTheme.DARK, theme === AppTheme.DARK);
        htmlElement.setAttribute("data-color-mode", theme);
        updateMetaThemeColor();
    };

    const applySystemTheme = () => {
        const isDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
        htmlElement.classList.toggle(AppTheme.DARK, isDark);
        htmlElement.setAttribute("data-color-mode", AppTheme.AUTO);
        updateMetaThemeColor();
    };

    const updateMetaThemeColor = () => {
        if (metaThemeColor) {
            const backgroundColor = getComputedStyle(htmlElement)
                .getPropertyValue("--background")
                .trim();
            // Assuming --background is a valid HSL color, adjust as needed
            metaThemeColor.setAttribute("content", `hsl(${backgroundColor})`);
        }
    };

    function getInitialTheme(): AppTheme | undefined {
        if (typeof localStorage === "undefined") {
            return AppTheme.AUTO;
        }

        const storedTheme = localStorage.getItem(
            LOCAL_STORAGE_THEME_KEY
        ) as AppTheme | null;
        if (storedTheme === AppTheme.LIGHT || storedTheme === AppTheme.DARK) {
            return storedTheme;
        }

        return AppTheme.AUTO;
    }

    return (
        <ThemeContext.Provider
            value={{ currentTheme, setLightTheme, setDarkTheme, setSystemTheme }}
        >
            {children}
        </ThemeContext.Provider>
    );
};
