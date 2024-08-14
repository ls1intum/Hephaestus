import { Component } from '@angular/core';
import { CounterComponent } from 'app/example/counter/counter.component';
import { HelloComponent } from 'app/example/hello/hello.component';

@Component({
  selector: 'app-main',
  standalone: true,
  imports: [CounterComponent, HelloComponent],
  templateUrl: './main.component.html'
})
export class MainComponent {}
