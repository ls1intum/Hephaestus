import { APP_INITIALIZER, ApplicationConfig, ErrorHandler, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAngularQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { BASE_PATH } from 'app/core/modules/openapi';
import { routes } from 'app/app.routes';
import { AnalyticsService } from './analytics.service';
import { securityInterceptor } from './core/security/security-interceptor';
import { EnvironmentService } from './environment.service';
import { TraceService } from '@sentry/angular';
import { SentryErrorHandler } from './core/sentry/sentry.error-handler';

function initializeApp(environmentService: EnvironmentService, analyticsService: AnalyticsService) {
  return () => {
    environmentService.loadEnv();
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
    { provide: APP_INITIALIZER, useFactory: initializeApp, multi: true, deps: [EnvironmentService, AnalyticsService] },
    {
      provide: BASE_PATH,
      useFactory: (environmentService: EnvironmentService) => environmentService.env.serverUrl,
      deps: [EnvironmentService]
    },
    { provide: ErrorHandler, useClass: SentryErrorHandler },
    { provide: TraceService, deps: [Router] },
    {
      provide: APP_INITIALIZER,
      useFactory: () => () => {},
      deps: [TraceService],
      multi: true
    }
  ]
};
