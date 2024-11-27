import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { SecurityStore } from './security-store.service';
import { from, switchMap } from 'rxjs';

export const securityInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloakService = inject(SecurityStore);

  // update access token to prevent 401s
  return from(keycloakService.updateToken()).pipe(
    switchMap(() => {
      // add bearer token to request
      const bearer = keycloakService.user()?.bearer;

      if (!bearer) {
        return next(req);
      }

      return next(
        req.clone({
          headers: req.headers.set('Authorization', `Bearer ${bearer}`)
        })
      );
    })
  );
};
