import { Component, inject } from '@angular/core';
import { injectMutation, injectQuery, injectQueryClient } from '@tanstack/angular-query-experimental';
import { AngularQueryDevtools } from '@tanstack/angular-query-devtools-experimental';
import { lastValueFrom } from 'rxjs';
import { HelloService } from 'app/core/modules/openapi';
import { ButtonComponent } from 'app/ui/button/button.component';

@Component({
  selector: 'app-hello',
  standalone: true,
  imports: [ButtonComponent, AngularQueryDevtools],
  templateUrl: './hello.component.html'
})
export class HelloComponent {
  helloService = inject(HelloService);
  queryClient = injectQueryClient();

  query = injectQuery(() => ({
    queryKey: ['hellos'],
    queryFn: async () => lastValueFrom(this.helloService.getAllHellos())
  }));

  mutation = injectMutation(() => ({
    mutationFn: () => lastValueFrom(this.helloService.addHello()),
    onSuccess: () =>
      this.queryClient.invalidateQueries({
        queryKey: ['hellos']
      })
  }));

  addHello() {
    this.mutation.mutate();
  }
}
