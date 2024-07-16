import { ApplicationConfig, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { BASE_PATH } from './core/modules/openapi';

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: BASE_PATH, useValue: "https://pokeapi.co" },
    provideExperimentalZonelessChangeDetection(),
    provideRouter(routes),
  ]
};
