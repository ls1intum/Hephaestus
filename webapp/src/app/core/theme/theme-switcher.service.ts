import { Injectable, signal } from '@angular/core';

export enum AppTheme {
  LIGHT = 'light',
  DARK = 'dark'
}

const IS_CLIENT_RENDER = typeof localStorage !== 'undefined';
const LOCAL_STORAGE_THEME_KEY = 'theme';

let selectedTheme: AppTheme | undefined = undefined;

if (IS_CLIENT_RENDER) {
  selectedTheme = (localStorage.getItem(LOCAL_STORAGE_THEME_KEY) as AppTheme) || undefined;
}

@Injectable({
  providedIn: 'root'
})
export class ThemeSwitcherService {
  currentTheme = signal<AppTheme | undefined>(selectedTheme);

  setLightTheme() {
    this.currentTheme.set(AppTheme.LIGHT);
    this.setToLocalStorage(AppTheme.LIGHT);
    this.removeClassFromHtml('dark');
    document.documentElement.setAttribute('data-color-mode', 'light');
  }

  setDarkTheme() {
    this.currentTheme.set(AppTheme.DARK);
    this.setToLocalStorage(AppTheme.DARK);
    this.addClassToHtml('dark');
    document.documentElement.setAttribute('data-color-mode', 'dark');
  }

  setSystemTheme() {
    this.currentTheme.set(undefined);
    this.removeFromLocalStorage();

    const isSystemDark = window?.matchMedia('(prefers-color-scheme: dark)').matches ?? false;
    if (isSystemDark) {
      this.addClassToHtml('dark');
    } else {
      this.removeClassFromHtml('dark');
    }

    document.documentElement.setAttribute('data-color-mode', 'auto');
  }

  private addClassToHtml(className: string) {
    if (IS_CLIENT_RENDER) {
      this.removeClassFromHtml(className);
      document.documentElement.classList.add(className);
    }
  }

  private removeClassFromHtml(className: string) {
    if (IS_CLIENT_RENDER) {
      document.documentElement.classList.remove(className);
    }
  }

  private setToLocalStorage(theme: AppTheme) {
    if (IS_CLIENT_RENDER) {
      localStorage.setItem(LOCAL_STORAGE_THEME_KEY, theme);
    }
  }

  private removeFromLocalStorage() {
    if (IS_CLIENT_RENDER) {
      localStorage.removeItem(LOCAL_STORAGE_THEME_KEY);
    }
  }
}
