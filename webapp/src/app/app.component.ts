import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HelloWorldComponent } from './hello-world/hello-world.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HelloWorldComponent],
  templateUrl: './app.component.html',
  styles: [],
})
export class AppComponent {
  title = 'webapp';
}
