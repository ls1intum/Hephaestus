import { Component, isDevMode } from '@angular/core';
import { AngularQueryDevtools } from '@tanstack/angular-query-devtools-experimental';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideAngularModule, Hammer, Sparkles } from 'lucide-angular';
import { ThemeSwitcherComponent } from 'app/core/theme/theme-switcher.component';
import { HlmButtonModule } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, LucideAngularModule, ThemeSwitcherComponent, RouterLink, RouterLinkActive, AngularQueryDevtools, HlmButtonModule],
  templateUrl: './app.component.html',
  styles: []
})
export class AppComponent {
  protected Hammer = Hammer;
  protected Sparkles = Sparkles;

  title = 'Hephaestus';

  isDevMode() {
    return isDevMode();
  }
}
