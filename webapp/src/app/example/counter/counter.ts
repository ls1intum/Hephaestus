import { signal } from "@angular/core";

export const counter = signal(0);

// While global state is possible, this is a good alternative:
// @Injectable({
//   providedIn: "root",
// })
// export class CounterService {
//
//   // this is the private writeable signal
//   private counterSignal = signal(0);
//
//   // this is the public read-only signal
//   readonly counter = this.counterSignal.asReadonly();
//
//   constructor() {
//     // inject any dependencies you need here
//   }
//
//   // anyone needing to modify the signal 
//   // needs to do so in a controlled way
//   incrementCounter() {
//     this.counterSignal.update((val) => val + 1);
//   }
// }