import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CounterComponent } from './example/counter/counter.component';
import { HelloComponent } from './example/hello/hello.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CounterComponent, HelloComponent],
  templateUrl: './app.component.html',
  styles: []
})
export class AppComponent {
  title = 'Hephaestus';
}
