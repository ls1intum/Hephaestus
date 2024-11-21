import { Component, isDevMode } from '@angular/core';
import { AngularQueryDevtools } from '@tanstack/angular-query-devtools-experimental';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { HeaderComponent } from '@app/core/header/header.component';
import { FooterComponent } from './core/footer/footer.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AngularQueryDevtools, HeaderComponent, FooterComponent],
  templateUrl: './app.component.html'
})
export class AppComponent {
  title = 'Hephaestus';

  isDevMode() {
    return isDevMode();
  }
}
