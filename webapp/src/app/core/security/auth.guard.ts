import { inject, Injectable, Injector } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { SecurityStore } from '@app/core/security/security-store.service';
import { filter, map, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {
  injector = inject(Injector);
  securityStore = inject(SecurityStore);
  router = inject(Router);

  canActivate(): Observable<boolean | UrlTree> {
    return toObservable(this.securityStore.loadedUser, { injector: this.injector }).pipe(
      filter(Boolean),
      map((user) => {
        if (!user.anonymous) {
          return true;
        }
        this.securityStore.signIn();
        return false;
      })
    );
  }
}
