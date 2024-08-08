import { Component, computed, effect, input, signal } from '@angular/core';
import { counter } from './counter';
import { AppButtonComponent } from 'app/ui/button/button.component';

interface CounterHistoryEntry {
  dec: number;
  hex: string;
}

@Component({
  selector: 'app-counter',
  standalone: true,
  imports: [AppButtonComponent],
  templateUrl: './counter.component.html'
})
export class CounterComponent {
  // we put all our application data inside signals! -> most optimal change detection and re-rendering possible

  // Input signal `input` without required is also possible but then it is `string | undefined`
  title = input.required<string>();
  byCount = input<number>(1);

  counter = counter; // We can share signals imported from file

  hexCounter = computed(() => {
    // Pitfall: conditional logic inside computed
    // When calling computed initially the dependency signal has to be called, otherwise it will not work
    return this.counter().toString(16);
  });

  counterHistory = signal<CounterHistoryEntry[]>([{ dec: 0, hex: '0' }]);

  constructor() {
    console.log(`counter value: ${this.counter()}`);

    // `effect`s goes in constructor (?)
    // `effectRef` is not necessary, only if needed
    // Runs once when the effect is declared to collect dependencies, and again when they change
    // const effectRef =
    effect((onCleanup) => {
      const currentCount = this.counter();
      const currentHexCount = this.hexCounter();

      console.log(`current values: ${currentCount}, ${currentHexCount}`);

      // By default we cannot change signals within an `effect` -> would cause an infinite loop

      onCleanup(() => {
        console.log('Perform cleanup action here');
      });
    });

    // effectRef.destroy() at any time! Usually not necessary

    // Readonly Example: (for when to expose something that should not be changed by someone else)
    // readonly counter = this.counterSignal.asReadonly();
  }

  increment() {
    console.log('Updating counter...');
    this.counter.update((counter) => counter + this.byCount());

    // Update values of a signal only through Signals API, e.g.`set()` and `update()`, not directly (i.e. `push()`)
    this.counterHistory.update((history) => [...history, { dec: this.counter(), hex: this.hexCounter() }]);
  }
}
