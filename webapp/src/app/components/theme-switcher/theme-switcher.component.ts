import { Component, inject } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ButtonComponent } from 'app/ui/button/button.component';
import { ThemeSwitcherService } from './theme-switcher.service';
import { animate, state, style, transition, trigger } from '@angular/animations';

@Component({
  selector: 'app-theme-switcher',
  standalone: true,
  imports: [ButtonComponent, LucideAngularModule],
  templateUrl: './theme-switcher.component.html',
  animations: [
    trigger('iconTrigger', [
      state('*', style({ transform: 'rotate(0deg)' })),
      transition('light => dark', animate('0.25s ease-out', style({ transform: 'rotate(90deg)' }))),
      transition('dark => light', animate('0.25s ease-out', style({ transform: 'rotate(360deg)' })))
    ])
  ]
})
export class ThemeSwitcherComponent {
  themeSwitcherService = inject(ThemeSwitcherService);

  toggleTheme() {
    if (this.themeSwitcherService.currentTheme() === 'dark') {
      this.themeSwitcherService.setLightTheme();
    } else {
      this.themeSwitcherService.setDarkTheme();
    }
  }
}
