import { ApplicationConfig, importProvidersFrom, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideAngularQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { LucideAngularModule, Home, Sun, Moon, Hammer } from 'lucide-angular';
import { environment } from 'environments/environment';
import { BASE_PATH } from 'app/core/modules/openapi';
import { routes } from 'app/app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideExperimentalZonelessChangeDetection(),
    provideRouter(routes),
    provideAngularQuery(new QueryClient()),
    provideHttpClient(withInterceptorsFromDi()),
    provideAnimationsAsync(),
    importProvidersFrom(LucideAngularModule.pick({ Home, Sun, Moon, Hammer })),
    { provide: BASE_PATH, useValue: environment.serverUrl }
  ]
};
