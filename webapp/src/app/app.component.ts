import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CounterComponent } from './example/counter/counter.component';
import { PokemonComponent } from './example/pokemon/pokemon.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CounterComponent, PokemonComponent],
  templateUrl: './app.component.html',
  styles: [],
})
export class AppComponent {
  title = 'Hephaestus';
}
