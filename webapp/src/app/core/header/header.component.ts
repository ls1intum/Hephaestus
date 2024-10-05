import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, Hammer } from 'lucide-angular';
import { injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { lastValueFrom } from 'rxjs';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { SecurityStore } from '@app/core/security/security-store.service';
import { ThemeSwitcherComponent } from '@app/core/theme/theme-switcher.component';
import { AdminService } from '@app/core/modules/openapi';
import { RequestFeatureComponent } from './request-feature/request-feature.component';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, ThemeSwitcherComponent, HlmButtonModule, RequestFeatureComponent]
})
export class HeaderComponent {
  protected Hammer = Hammer;

  securityStore = inject(SecurityStore);
  signedIn = this.securityStore.signedIn;

  queryClient = injectQueryClient();

  protected signOut() {
    this.securityStore.signOut();
    this.queryClient.invalidateQueries({ queryKey: ['me'] });
  }

  protected signIn() {
    this.securityStore.signIn();
    this.queryClient.invalidateQueries({ queryKey: ['me'] });
  }
}
