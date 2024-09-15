import { Component, isDevMode } from '@angular/core';
import { AngularQueryDevtools } from '@tanstack/angular-query-devtools-experimental';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideAngularModule, Hammer } from 'lucide-angular';
import { ThemeSwitcherComponent } from './components/theme-switcher/theme-switcher.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, LucideAngularModule, ThemeSwitcherComponent, RouterLink, RouterLinkActive, AngularQueryDevtools],
  templateUrl: './app.component.html',
  styles: []
})
export class AppComponent {
  protected Hammer = Hammer;

  title = 'Hephaestus';

  isDevMode() {
    return isDevMode();
  }
}
