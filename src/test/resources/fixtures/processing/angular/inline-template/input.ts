import { Component } from '@angular/core'

@Component({
  selector: 'app-card',
  template: `
    <section
      class="text-center p-4 flex bg-red-500 font-bold"
      [class]="active ? 'font-bold flex p-4' : ''"
      [ngClass]="{ 'tracking-wide text-xs px-2 py-0.5': compact }"
    ></section>
  `,
})
export class CardComponent {}
