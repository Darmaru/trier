import { Component } from '@angular/core'

@Component({
  selector: 'app-card',
  template: `
    <section
      class="flex bg-red-500 p-4 text-center font-bold"
      [class]="active ? 'flex p-4 font-bold' : ''"
      [ngClass]="{ 'px-2 py-0.5 text-xs tracking-wide': compact }"
    ></section>
  `,
})
export class CardComponent {}
