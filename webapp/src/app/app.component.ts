import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CounterComponent } from './example/counter/counter.component';
import { HelloComponent } from './example/hello/hello.component';
import { ThemeSwitcherComponent } from './components/theme-switcher/theme-switcher.component';
import { LucideAngularModule } from 'lucide-angular';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CounterComponent, HelloComponent, LucideAngularModule, ThemeSwitcherComponent],
  templateUrl: './app.component.html',
  styles: []
})
export class AppComponent {
  title = 'Hephaestus';
}
