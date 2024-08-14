import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ThemeSwitcherComponent } from './components/theme-switcher/theme-switcher.component';
import { LucideAngularModule } from 'lucide-angular';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, LucideAngularModule, ThemeSwitcherComponent, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styles: []
})
export class AppComponent {
  title = 'Hephaestus';
}
