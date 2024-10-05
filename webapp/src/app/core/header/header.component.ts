import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, Hammer } from 'lucide-angular';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';
import { SecurityStore } from '@app/core/security/security-store.service';
import { ThemeSwitcherComponent } from '@app/core/theme/theme-switcher.component';
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

  protected signOut() {
    this.securityStore.signOut();
  }

  protected signIn() {
    this.securityStore.signIn();
  }
}
