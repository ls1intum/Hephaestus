import { ApplicationConfig, importProvidersFrom, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideAngularQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule, Home, Sun, Moon, Hammer } from 'lucide-angular';
import { routes } from './app.routes';
import { BASE_PATH } from './core/modules/openapi';

export const appConfig: ApplicationConfig = {
  providers: [
    provideExperimentalZonelessChangeDetection(),
    provideRouter(routes),
    provideAngularQuery(new QueryClient()),
    { provide: BASE_PATH, useValue: 'http://localhost:8080' },
    provideHttpClient(withInterceptorsFromDi()),
    importProvidersFrom([BrowserAnimationsModule, LucideAngularModule.pick({ Home, Sun, Moon, Hammer })])
  ]
};
