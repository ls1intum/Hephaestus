import { APP_INITIALIZER, ApplicationConfig, ErrorHandler, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAngularQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { environment } from 'environments/environment';
import { BASE_PATH } from 'app/core/modules/openapi';
import { routes } from 'app/app.routes';
import { AnalyticsService } from './analytics.service';
import { securityInterceptor } from './core/security/security-interceptor';
import { createErrorHandler, TraceService } from '@sentry/angular';

function initializeAnalytics(analyticsService: AnalyticsService): () => void {
  return () => {
    analyticsService.initialize();
  };
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideExperimentalZonelessChangeDetection(),
    provideRouter(routes),
    provideAngularQuery(new QueryClient()),
    provideHttpClient(withInterceptors([securityInterceptor])),
    provideAnimationsAsync(),
    { provide: BASE_PATH, useValue: environment.serverUrl },
    { provide: APP_INITIALIZER, useFactory: initializeAnalytics, multi: true, deps: [AnalyticsService] },
    { provide: ErrorHandler, useValue: createErrorHandler() },
    { provide: TraceService, deps: [Router] },
    {
        provide: APP_INITIALIZER,
        useFactory: () => () => {},
        deps: [TraceService],
        multi: true,
    },
  ]
};
