let enabled = false;

export function enableButtonRipple(): void {
  if (enabled || typeof document === 'undefined') {
    return;
  }
  enabled = true;

  document.addEventListener('click', (event: MouseEvent) => {
    const button = (event.target as HTMLElement | null)?.closest('button');
    if (!button || button.disabled) {
      return;
    }

    const rect = button.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height) * 2;

    const ripple = document.createElement('span');
    ripple.className = 'ripple';
    ripple.style.width = `${size}px`;
    ripple.style.height = `${size}px`;
    ripple.style.left = `${event.clientX - rect.left - size / 2}px`;
    ripple.style.top = `${event.clientY - rect.top - size / 2}px`;

    button.appendChild(ripple);
    setTimeout(() => ripple.remove(), 700);
  });
}
