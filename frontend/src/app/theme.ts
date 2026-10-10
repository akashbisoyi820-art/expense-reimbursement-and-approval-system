type Theme = 'light' | 'dark';

interface ViewTransitionLike {
  ready: Promise<void>;
  finished: Promise<void>;
}

type DocumentWithTransitions = Document & {
  startViewTransition?: (update: () => void) => ViewTransitionLike;
};

const STORAGE_KEY = 'theme';

const SUN_ICON =
  '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">' +
  '<circle cx="12" cy="12" r="4.5"/>' +
  '<path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8"/>' +
  '</svg>';

const MOON_ICON =
  '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true">' +
  '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>' +
  '</svg>';

let started = false;

function readSavedTheme(): Theme | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value === 'dark' || value === 'light' ? value : null;
  } catch {
    return null;
  }
}

function currentTheme(): Theme {
  return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
}

function syncLabel(): void {
  const button = document.querySelector('.theme-toggle');
  if (!button) {
    return;
  }
  const label = currentTheme() === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';
  button.setAttribute('aria-label', label);
  button.setAttribute('title', label);
}

function applyTheme(theme: Theme): void {
  document.documentElement.setAttribute('data-theme', theme);
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    /* storage not available */
  }
  syncLabel();
}

function toggleTheme(event: MouseEvent): void {
  const next: Theme = currentTheme() === 'dark' ? 'light' : 'dark';
  const root = document.documentElement;
  const doc = document as DocumentWithTransitions;
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  if (!doc.startViewTransition || reduceMotion) {
    applyTheme(next);
    return;
  }

  // The reveal starts where the button was clicked (or from its centre for keyboard use)
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  const x = event.detail === 0 ? rect.left + rect.width / 2 : event.clientX;
  const y = event.detail === 0 ? rect.top + rect.height / 2 : event.clientY;
  const radius = Math.hypot(
    Math.max(x, window.innerWidth - x),
    Math.max(y, window.innerHeight - y)
  );

  root.classList.add('theme-switching');
  const transition = doc.startViewTransition(() => applyTheme(next));

  transition.ready
    .then(() => {
      root.animate(
        {
          clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`]
        },
        {
          duration: 700,
          easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
          pseudoElement: '::view-transition-new(root)'
        }
      );
    })
    .catch(() => undefined);

  transition.finished
    .catch(() => undefined)
    .then(() => root.classList.remove('theme-switching'));
}

function mountButton(): void {
  if (document.querySelector('.theme-toggle')) {
    return;
  }

  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'theme-toggle';
  button.innerHTML =
    `<span class="icon sun">${SUN_ICON}</span><span class="icon moon">${MOON_ICON}</span>`;
  button.addEventListener('click', toggleTheme);

  document.body.appendChild(button);
  syncLabel();
}

export function initTheme(): void {
  if (started || typeof document === 'undefined') {
    return;
  }
  started = true;

  // First visit: light theme. After that, the saved choice is used.
  document.documentElement.setAttribute('data-theme', readSavedTheme() ?? 'light');

  if (document.body) {
    mountButton();
  } else {
    document.addEventListener('DOMContentLoaded', mountButton);
  }
}
