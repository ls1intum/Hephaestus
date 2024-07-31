import { Component, inject, signal } from '@angular/core';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { AngularQueryDevtools } from '@tanstack/angular-query-devtools-experimental'
import { lastValueFrom, tap } from 'rxjs';
import { HelloControllerService } from 'app/core/modules/openapi';
import { AppButtonComponent } from 'app/ui/button/button/button.component';

@Component({
  selector: 'hello',
  standalone: true,
  imports: [AppButtonComponent, AngularQueryDevtools],
  templateUrl: './hello.component.html'
})
export class PokemonComponent {
  helloControllerService = inject(HelloControllerService)
  queryClient = injectQueryClient()

  query = injectQuery(() => ({
    queryKey: ['hellos'],
    queryFn: async () => lastValueFrom(this.helloControllerService.getAllHellos()),
    })
  );

  mutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.helloControllerService.addHello()),
    onSuccess: () => 
      this.queryClient.invalidateQueries({
        queryKey: ['hellos'],
      }),
    })
  );

  addHello() {
    this.mutation.mutate();
  }
}