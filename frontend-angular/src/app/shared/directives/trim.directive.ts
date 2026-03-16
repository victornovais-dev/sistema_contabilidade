import { Directive, ElementRef, inject } from "@angular/core";
import { NgControl } from "@angular/forms";

@Directive({
  selector: "input[appTrim], textarea[appTrim]",
  host: {
    "(blur)": "onBlur()",
  },
})
export class TrimDirective {
  private readonly elementRef = inject(ElementRef<HTMLInputElement | HTMLTextAreaElement>);
  private readonly ngControl = inject(NgControl, { optional: true, self: true });

  onBlur(): void {
    const currentValue = this.elementRef.nativeElement.value;
    const trimmedValue = currentValue.trim();

    if (currentValue === trimmedValue) {
      return;
    }

    this.elementRef.nativeElement.value = trimmedValue;
    this.ngControl?.control?.setValue(trimmedValue);
  }
}
