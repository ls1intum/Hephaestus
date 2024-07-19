import { ApplicationConfig, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import {
  provideAngularQuery,
  QueryClient,
} from '@tanstack/angular-query-experimental'
import { routes } from './app.routes';
import { BASE_PATH } from './core/modules/openapi';

export const appConfig: ApplicationConfig = {
  providers: [
    provideExperimentalZonelessChangeDetection(),
    provideRouter(routes),
    provideAngularQuery(new QueryClient()),
    { provide: BASE_PATH, useValue: "https://pokeapi.co" },
    provideHttpClient(withInterceptorsFromDi())
  ]
};