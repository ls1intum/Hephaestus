import React from "react";
import { useTheme, AppTheme } from "./theme-context";
import { Sun, Moon } from "lucide-react";
import { Button } from "./ui/button";

const ThemeSwitcher: React.FC = () => {
    const { currentTheme, setLightTheme, setDarkTheme } = useTheme();

    const toggleTheme = () => {
        if (currentTheme === AppTheme.DARK) {
            setLightTheme();
        } else if (currentTheme === AppTheme.LIGHT) {
            setDarkTheme();
        } else {
            // If 'auto', decide based on system preference or default to dark
            const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
            if (prefersDark) {
                setLightTheme();
            } else {
                setDarkTheme();
            }
        }
    };

    const getIcon = () => {
        if (currentTheme === AppTheme.DARK) {
            return <Moon className="icon" />;
        } else if (currentTheme === AppTheme.LIGHT) {
            return <Sun className="icon" />;
        } else {
            // 'auto' - determine system preference
            const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
            return prefersDark ? <Moon className="icon" /> : <Sun className="icon" />;
        }
    };

    return (
        <Button
            variant="outline"
            size="icon"
            onClick={toggleTheme}
            aria-label="Toggle Theme"
        >
            {getIcon()}
        </Button>
    );
};

export default ThemeSwitcher;
