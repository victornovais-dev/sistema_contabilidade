(() => {
  const measureMenuHeight = (menu, visibleItems = 7) => {
    if (!(menu instanceof HTMLElement)) return;

    const options = Array.from(menu.querySelectorAll(".custom-select-option"));
    const shouldScroll = options.length > visibleItems;
    menu.classList.toggle("is-scroll", shouldScroll);

    if (!shouldScroll) {
      menu.style.maxHeight = "";
      return;
    }

    window.requestAnimationFrame(() => {
      const visibleOptions = options.slice(0, visibleItems);
      const optionsHeight = visibleOptions.reduce(
        (total, option) => total + option.getBoundingClientRect().height,
        0,
      );
      const styles = window.getComputedStyle(menu);
      const paddingTop = Number.parseFloat(styles.paddingTop || "0");
      const paddingBottom = Number.parseFloat(styles.paddingBottom || "0");
      menu.style.maxHeight = `${optionsHeight + paddingTop + paddingBottom}px`;
    });
  };

  window.createRoleDropdown = ({ select, onChange }) => {
    if (!(select instanceof HTMLSelectElement)) {
      return null;
    }

    const wrapper = select.closest("[data-custom-select]");
    const trigger = wrapper?.querySelector(".custom-select-trigger");
    const menu = wrapper?.querySelector(".custom-select-menu");

    if (!(wrapper instanceof HTMLElement)) return null;
    if (!(trigger instanceof HTMLButtonElement)) return null;
    if (!(menu instanceof HTMLElement)) return null;

    const sync = () => {
      const selected = select.selectedOptions?.[0] || null;
      const label = selected ? String(selected.textContent || "").trim() : "";
      trigger.textContent = label || "Selecione";
      menu.querySelectorAll(".custom-select-option").forEach((node) => {
        if (!(node instanceof HTMLElement)) return;
        node.classList.toggle("is-active", (node.dataset.value || "") === select.value);
      });
    };

    const close = () => {
      menu.hidden = true;
      trigger.setAttribute("aria-expanded", "false");
    };

    const open = () => {
      menu.hidden = false;
      trigger.setAttribute("aria-expanded", "true");
      measureMenuHeight(menu);
    };

    const clear = () => {
      select.innerHTML = '<option value="" disabled selected>Selecione</option>';
      menu.innerHTML = "";
      menu.hidden = true;
      menu.classList.remove("is-scroll");
      menu.style.maxHeight = "";
      trigger.textContent = "Selecione";
      trigger.setAttribute("aria-expanded", "false");
    };

    const setOptions = (options) => {
      const safeOptions = Array.isArray(options) ? options : [];
      select.innerHTML = '<option value="" disabled selected>Selecione</option>';
      menu.innerHTML = "";

      safeOptions.forEach((value) => {
        const optionValue = String(value || "").trim();
        if (!optionValue) return;

        const option = document.createElement("option");
        option.value = optionValue;
        option.textContent = optionValue;
        select.appendChild(option);

        const button = document.createElement("button");
        button.className = "custom-select-option";
        button.type = "button";
        button.setAttribute("role", "option");
        button.dataset.value = optionValue;
        button.textContent = optionValue;
        menu.appendChild(button);
      });

      menu.classList.remove("is-scroll");
      menu.style.maxHeight = "";
      sync();
    };

    const setValue = (value) => {
      select.value = String(value || "").trim();
      sync();
    };

    trigger.addEventListener("click", (event) => {
      event.preventDefault();
      if (menu.hidden) {
        open();
        return;
      }
      close();
    });

    menu.addEventListener("click", (event) => {
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;
      const option = target.closest(".custom-select-option");
      if (!(option instanceof HTMLElement)) return;

      select.value = option.dataset.value || "";
      select.dispatchEvent(new Event("change", { bubbles: true }));
      close();
    });

    document.addEventListener(
      "mousedown",
      (event) => {
        const target = event.target;
        if (!(target instanceof Node)) return;
        if (trigger.contains(target) || menu.contains(target)) return;
        close();
      },
      { capture: true },
    );

    select.addEventListener("change", () => {
      sync();
      if (typeof onChange === "function") {
        onChange(select.value || "");
      }
    });

    sync();

    return {
      clear,
      close,
      open,
      setOptions,
      setValue,
      sync,
      trigger,
      menu,
      select,
    };
  };
})();
