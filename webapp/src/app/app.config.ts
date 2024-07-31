import { ApplicationConfig, importProvidersFrom, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import {
  provideAngularQuery,
  QueryClient,
} from '@tanstack/angular-query-experimental'
import {LucideAngularModule, Home } from "lucide-angular";
import { routes } from './app.routes';
import { BASE_PATH } from './core/modules/openapi';


export const appConfig: ApplicationConfig = {
  providers: [
    provideExperimentalZonelessChangeDetection(),
    provideRouter(routes),
    provideAngularQuery(new QueryClient()),
    { provide: BASE_PATH, useValue: "http://localhost:8080" },
    provideHttpClient(withInterceptorsFromDi()),
    importProvidersFrom(LucideAngularModule.pick({ Home }))
  ]
};