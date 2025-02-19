import { ApplicationConfig, ErrorHandler, provideExperimentalZonelessChangeDetection, inject, provideAppInitializer } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { QueryClient, provideTanStackQuery, withDevtools } from '@tanstack/angular-query-experimental';
import { environment } from 'environments/environment';
import { BASE_PATH } from 'app/core/modules/openapi';
import { routes } from 'app/app.routes';
import { securityInterceptor } from './core/security/security-interceptor';
import { TraceService } from '@sentry/angular';
import { SentryErrorHandler } from './core/sentry/sentry.error-handler';

export const appConfig: ApplicationConfig = {
  providers: [
    provideExperimentalZonelessChangeDetection(),
    provideRouter(routes),
    provideTanStackQuery(
      new QueryClient(),
      withDevtools(() => ({ loadDevtools: 'auto' }))
    ),
    provideHttpClient(withInterceptors([securityInterceptor])),
    provideAnimationsAsync(),
    { provide: BASE_PATH, useValue: environment.serverUrl },
    { provide: ErrorHandler, useClass: SentryErrorHandler },
    { provide: TraceService, deps: [Router] },
    provideAppInitializer(() => {
      const initializerFn = (() => () => {
        inject(TraceService);
      })();
      return initializerFn();
    })
  ]
};
