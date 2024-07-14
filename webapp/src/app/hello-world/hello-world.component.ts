import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'hello-world',
  standalone: true,
  imports: [],
  templateUrl: './hello-world.component.html',
  styles: ``,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HelloWorldComponent {

}
