import { Injectable, signal } from '@angular/core';

export enum AppTheme {
  LIGHT = 'light',
  DARK = 'dark'
}

const LOCAL_STORAGE_THEME_KEY = 'theme';

@Injectable({
  providedIn: 'root'
})
export class ThemeSwitcherService {
  private htmlElement = document.documentElement;
  private metaThemeColor = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');

  currentTheme = signal<AppTheme | 'auto' | undefined>(this.getInitialTheme());

  constructor() {
    if (this.currentTheme() === 'auto') {
      this.applySystemTheme();
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', this.handleSystemThemeChange);
    }
  }

  setLightTheme() {
    this.applyTheme(AppTheme.LIGHT);
  }

  setDarkTheme() {
    this.applyTheme(AppTheme.DARK);
  }

  setSystemTheme() {
    this.currentTheme.set('auto');
    localStorage.removeItem(LOCAL_STORAGE_THEME_KEY);
    this.applySystemTheme();
    this.updateMetaThemeColor();
  }

  private applyTheme(theme: AppTheme) {
    this.currentTheme.set(theme);
    localStorage.setItem(LOCAL_STORAGE_THEME_KEY, theme);
    this.htmlElement.classList.toggle(AppTheme.DARK, theme === AppTheme.DARK);
    this.htmlElement.setAttribute('data-color-mode', theme);
    this.updateMetaThemeColor();
  }

  private applySystemTheme() {
    const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    this.htmlElement.classList.toggle(AppTheme.DARK, isDark);
    this.htmlElement.setAttribute('data-color-mode', 'auto');
    this.updateMetaThemeColor();
  }

  private handleSystemThemeChange = (event: MediaQueryListEvent) => {
    if (this.currentTheme() === 'auto') {
      this.htmlElement.classList.toggle(AppTheme.DARK, event.matches);
      this.updateMetaThemeColor();
    }
  };

  private updateMetaThemeColor() {
    if (this.metaThemeColor) {
      const backgroundColor = getComputedStyle(this.htmlElement).getPropertyValue('--background').trim();
      this.metaThemeColor.setAttribute('content', `hsl(${backgroundColor})`);
    }
  }

  private getInitialTheme(): AppTheme | 'auto' | undefined {
    if (typeof localStorage === 'undefined') {
      return 'auto';
    }

    const storedTheme = localStorage.getItem(LOCAL_STORAGE_THEME_KEY) as AppTheme | null;
    if (storedTheme === AppTheme.LIGHT || storedTheme === AppTheme.DARK) {
      return storedTheme;
    }

    return 'auto';
  }
}
