import { Component, inject, signal } from '@angular/core';
import { PokemonService } from '../../core/modules/openapi';
import { injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { AngularQueryDevtools } from '@tanstack/angular-query-devtools-experimental'
import { fromEvent, lastValueFrom } from 'rxjs';

@Component({
  selector: 'pokemon',
  standalone: true,
  imports: [AngularQueryDevtools],
  templateUrl: './pokemon.component.html'
})
export class PokemonComponent {
  #pokemonService = inject(PokemonService)
  queryClient = injectQueryClient()

  pokemonId = signal(0);

  query = injectQuery(() => ({
    enabled: this.pokemonId() > 0,
    queryKey: ['pokemon', this.pokemonId()],
    queryFn: async (context) => {
      // Cancels the request when component is destroyed before the request finishes
      const abort$ = fromEvent(context.signal, 'abort')
      return lastValueFrom(this.#pokemonService.pokemonRetrieve(this.pokemonId().toString()))
    },
  }))

  incrementId() {
    this.pokemonId.update(id => id + 1);
  }

  decrementId() {
    this.pokemonId.update(id => id - 1);
  }
}