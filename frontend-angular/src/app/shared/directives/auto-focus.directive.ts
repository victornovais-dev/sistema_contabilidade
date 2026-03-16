import { Directive, ElementRef, afterNextRender, booleanAttribute, inject, input } from "@angular/core";

@Directive({
  selector: "[appAutoFocus]",
})
export class AutoFocusDirective {
  private readonly elementRef = inject(ElementRef<HTMLElement>);

  readonly enabled = input(true, { alias: "appAutoFocus", transform: booleanAttribute });
  readonly delay = input(0);

  constructor() {
    afterNextRender(() => {
      if (!this.enabled()) {
        return;
      }

      window.setTimeout(() => {
        this.elementRef.nativeElement.focus();
      }, this.delay());
    });
  }
}
