import { AfterViewInit, Component, ElementRef, NgZone, OnDestroy, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

interface NavLink {
  id: string;
  label: string;
}

interface Feature {
  icon: string;
  title: string;
  text: string;
}

interface Tab {
  key: string;
  title: string;
  text: string;
  points: string[];
}

interface Step {
  title: string;
  who: string;
  text: string;
}

interface FlowNode {
  title: string;
  text: string;
}

interface Tech {
  name: string;
  role: string;
}

interface Member {
  name: string;
  module: string;
  work: string;
}

interface Faq {
  q: string;
  a: string;
}

interface Metric {
  value: number;
  suffix: string;
  label: string;
}

// Simple 24 x 24 outline icons. Each icon is a list of SVG path strings.
const ICONS: Record<string, string[]> = {
  clock: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z', 'M12 7.5V12l3 2'],
  chat: ['M4 5h16v11H9.5L4 20.5z'],
  search: ['M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z', 'M20 20l-4.2-4.2'],
  rupee: ['M6 5h12', 'M6 9.5h12', 'M9.5 5c4.5 0 4.5 8 0 8H7l8 6'],
  chart: ['M3 20h18', 'M6 20v-7', 'M11 20V6', 'M16 20v-10'],
  card: ['M3 6h18v12H3z', 'M3 10.5h18', 'M7 15h3'],
  shield: ['M12 3l8 3v6c0 4.8-3.3 8-8 9-4.7-1-8-4.2-8-9V6z', 'M8.5 12l2.3 2.3 4.7-4.8'],
  spark: ['M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z', 'M18.5 16.5v3', 'M17 18h3']
};

const clamp = (value: number, min = 0, max = 1) => Math.min(max, Math.max(min, value));

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent implements AfterViewInit, OnDestroy {
  loggedIn = !!localStorage.getItem('token');
  homeLink = localStorage.getItem('role') === 'EMPLOYEE' ? '/dashboard' : '/approvals';
  year = new Date().getFullYear();
  githubUrl = 'https://github.com/akashbisoyi820-art/expense-reimbursement-and-approval-system';

  menu = signal(false);
  active = signal('top');
  tab = signal(0);
  openFaq = signal<number | null>(0);

  navLinks: NavLink[] = [
    { id: 'product', label: 'Product' },
    { id: 'features', label: 'Features' },
    { id: 'how', label: 'How it works' },
    { id: 'security', label: 'Security' },
    { id: 'team', label: 'Team' },
    { id: 'faq', label: 'FAQ' }
  ];

  heroBars = [38, 62, 48, 80, 58, 92, 70, 54, 86];

  techStrip = [
    'Spring Boot',
    'Angular',
    'MySQL',
    'Spring Security',
    'JWT',
    'Chart.js',
    'Frankfurter API'
  ];

  tabs: Tab[] = [
    {
      key: 'Employee',
      title: 'Submit in minutes, track to the last rupee',
      text: 'Fill in a claim, pick a currency and attach the receipt. The policy is checked while you type, so there are no surprises later.',
      points: [
        'Claim in 9 currencies with a live rate preview',
        'See the policy limits before you submit',
        'Follow every claim on its own timeline',
        'Chat with the approver on the claim itself'
      ]
    },
    {
      key: 'Manager',
      title: 'Review with the full picture',
      text: 'Every pending claim arrives with its receipt, its policy warnings and its comments, so a decision takes seconds.',
      points: [
        'Over-limit claims are flagged, with the reason',
        'A claim that needs a receipt cannot be approved without one',
        'Reject with a reason the employee will see',
        'Search every claim by employee and department'
      ]
    },
    {
      key: 'Admin',
      title: 'Control the policy and pay with a paper trail',
      text: 'Set the rules once, then reimburse with the payment method, the reference number and the date recorded on every claim.',
      points: [
        'Edit limits per category, with a history of every change',
        'Record how and when each claim was paid',
        'Spending charts for the whole company',
        'A live feed of every action in the system'
      ]
    }
  ];

  features: Feature[] = [
    {
      icon: 'clock',
      title: 'Complete audit trail',
      text: 'Every submission, approval, rejection and payment is written to a permanent timeline, with who did it and when.'
    },
    {
      icon: 'chat',
      title: 'Comments on every claim',
      text: 'Employees and approvers discuss a claim in a thread that lives on the claim itself.'
    },
    {
      icon: 'search',
      title: 'Search and filters',
      text: 'Find any claim by keyword, status, category, department, date range or amount. Sort and page through results.'
    },
    {
      icon: 'rupee',
      title: 'Multi-currency claims',
      text: 'Claim in 9 currencies. The live rate is saved with each claim, so an old claim never changes.'
    },
    {
      icon: 'chart',
      title: 'Spending analytics',
      text: 'See spending by category, status, month and department. Click a chart to open the claims behind it.'
    },
    {
      icon: 'card',
      title: 'Payment records',
      text: 'Every reimbursement stores the method, the reference number and the date. A bank account number is never stored.'
    },
    {
      icon: 'shield',
      title: 'Policy engine',
      text: 'Set limits per category. Over-limit claims are flagged for the manager, and a missing required receipt is blocked.'
    },
    {
      icon: 'spark',
      title: 'Expense assistant',
      text: 'A rule-based chat that answers from your real policy and your own claims. No outside service is involved.'
    }
  ];

  metrics: Metric[] = [
    { value: 9, suffix: '', label: 'Currencies with live exchange rates' },
    { value: 3, suffix: '', label: 'Roles, each with its own view' },
    { value: 5, suffix: '', label: 'Policy categories you control' },
    { value: 100, suffix: '%', label: 'Of actions kept in the audit trail' }
  ];

  steps: Step[] = [
    {
      title: 'Submit',
      who: 'Employee',
      text: 'Fill in the claim, choose a currency and attach the receipt. The policy is checked instantly.'
    },
    {
      title: 'Review',
      who: 'Manager',
      text: 'See the receipt, the warnings and the comments, then approve, or reject with a reason.'
    },
    {
      title: 'Reimburse',
      who: 'Admin',
      text: 'Record the payment: the method, the reference number and the date.'
    },
    {
      title: 'Track',
      who: 'Everyone',
      text: 'The status, the full timeline and the payment stay visible on the claim.'
    }
  ];

  securityPoints = [
    'Passwords are stored only as BCrypt hashes',
    'Stateless JWT sessions, with roles checked on the server',
    'Receipts are validated for type and size and stored under random names',
    'Only the owner, managers and admins can open a claim',
    'Every change lands in the audit trail'
  ];

  flow: FlowNode[] = [
    { title: 'The browser sends a request', text: 'Every call carries a signed token (JWT).' },
    { title: 'The token is verified', text: 'The server checks the signature and the expiry first.' },
    { title: 'The role is checked', text: 'Employee, manager and admin rules are enforced on the server.' },
    { title: 'Business rules run', text: 'Policy limits, receipt rules and status rules are applied.' },
    { title: 'It is recorded and stored', text: 'The change is saved with an audit entry in MySQL.' }
  ];

  tech: Tech[] = [
    { name: 'Spring Boot', role: 'REST API and business rules' },
    { name: 'Spring Security + JWT', role: 'Authentication and roles' },
    { name: 'MySQL', role: 'Claims, users, policy and audit data' },
    { name: 'Angular', role: 'The web application' },
    { name: 'Chart.js', role: 'Spending charts' },
    { name: 'Frankfurter API', role: 'Live exchange rates' }
  ];

  // Type the real names of the team members here (for example name: 'Asha Verma').
  team: Member[] = [
    {
      name: '',
      module: 'Authentication and Security',
      work: 'JWT login, BCrypt, role-based access, the login and register pages.'
    },
    {
      name: '',
      module: 'Employee Expense Submission',
      work: 'Claims, the employee dashboard, the status filter and summary cards.'
    },
    {
      name: '',
      module: 'Approval Workflow',
      work: 'Pending and approved lists, approve, reject, reimburse and the approvals page.'
    },
    {
      name: '',
      module: 'Receipts and Database',
      work: 'Receipt validation and storage, the MySQL schema and the project setup.'
    },
    {
      name: '',
      module: 'UI, Testing and Documentation',
      work: 'The design, the animations, API testing, the README and the demo.'
    }
  ];

  faqs: Faq[] = [
    {
      q: 'Does the app move real money?',
      a: 'No. It records and approves reimbursements and keeps the payment record: the method, the reference number and the date. The actual transfer happens through your normal payment process.'
    },
    {
      q: 'Which currencies are supported?',
      a: 'INR, USD, EUR, GBP, AED, SGD, AUD, CAD and JPY. The live exchange rate is fetched when a claim is submitted and saved with it, so a later change in the rate never alters an old claim.'
    },
    {
      q: 'What happens when a claim is over the policy limit?',
      a: 'It is still submitted, but the manager sees an Over limit warning with the reason. A claim that needs a receipt cannot be submitted, or approved, without one.'
    },
    {
      q: 'Who can see my claim?',
      a: 'Only you, managers and admins. Employees cannot open each other\'s claims, and every access is checked on the server.'
    },
    {
      q: 'Can the policy be changed?',
      a: 'Yes, by admins. Limits are set per category, and every change is kept in a history that shows who changed what and when. Changes apply to claims submitted afterwards.'
    },
    {
      q: 'Is the assistant an AI model?',
      a: 'It is rule-based. It understands a fixed set of topics, answers from the real policy and your own claims, and says so when it does not understand. No data leaves the system.'
    },
    {
      q: 'What is it built with?',
      a: 'Spring Boot and Spring Security (JWT) on the backend, MySQL for the data, and Angular on the frontend, with Chart.js for charts and the Frankfurter API for exchange rates.'
    }
  ];

  private cleanups: Array<() => void> = [];

  constructor(
    private host: ElementRef<HTMLElement>,
    private zone: NgZone
  ) {}

  ngAfterViewInit() {
    // Scroll and mouse events run outside Angular, so the page is not re-checked on every event
    this.zone.runOutsideAngular(() => {
      this.setupReveal();
      this.setupScroll();
      this.setupPointer();
      this.setupTabs();
    });
  }

  ngOnDestroy() {
    this.cleanups.forEach((fn) => fn());
    this.cleanups = [];
  }

  icon(name: string): string[] {
    return ICONS[name] ?? [];
  }

  initials(name: string): string {
    const parts = name.trim().split(/\s+/).filter(Boolean);
    return parts
      .slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase())
      .join('');
  }

  go(id: string, event: Event) {
    event.preventDefault();
    this.menu.set(false);
    this.host.nativeElement
      .querySelector(`#${id}`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  toggleFaq(index: number) {
    this.openFaq.set(this.openFaq() === index ? null : index);
  }

  // ---------- Reveal on scroll and counters ----------

  private setupReveal() {
    const root = this.host.nativeElement;
    const items = Array.from(root.querySelectorAll<HTMLElement>('.reveal'));
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const show = (el: HTMLElement, instant: boolean) => {
      el.classList.add('in');
      if (el.hasAttribute('data-count')) {
        this.runCounter(el, instant);
      }
      el.querySelectorAll<HTMLElement>('[data-count]').forEach((c) => this.runCounter(c, instant));
    };

    if (reduce || !('IntersectionObserver' in window)) {
      items.forEach((el) => show(el, true));
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            show(entry.target as HTMLElement, false);
            observer.unobserve(entry.target);
          }
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -6% 0px' }
    );

    items.forEach((el) => observer.observe(el));
    this.cleanups.push(() => observer.disconnect());
  }

  private runCounter(el: HTMLElement, instant: boolean) {
    if (el.dataset['done']) {
      return;
    }
    el.dataset['done'] = '1';

    const target = Number(el.dataset['count'] ?? 0);
    const suffix = el.dataset['suffix'] ?? '';
    const format = (value: number) => Math.round(value).toLocaleString('en-IN') + suffix;

    if (instant) {
      el.textContent = format(target);
      return;
    }

    const start = performance.now();
    const duration = 1700;
    const step = (now: number) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3);
      el.textContent = format(target * eased);
      if (t < 1) {
        requestAnimationFrame(step);
      }
    };
    requestAnimationFrame(step);
  }

  // ---------- Scroll: progress bar, navbar, scroll-spy, hero parallax, step line ----------

  private setupScroll() {
    const root = this.host.nativeElement;
    const fill = root.querySelector<HTMLElement>('.bar-fill');
    const nav = root.querySelector<HTMLElement>('.nav');
    const hero = root.querySelector<HTMLElement>('.hero');
    const steps = root.querySelector<HTMLElement>('.steps');
    const stepItems = Array.from(root.querySelectorAll<HTMLElement>('.step'));
    const ids = ['top', 'product', 'features', 'how', 'security', 'team', 'faq'];
    const sections = ids
      .map((id) => root.querySelector<HTMLElement>(`#${id}`))
      .filter((el): el is HTMLElement => el !== null);

    let ticking = false;
    let current = 'top';

    const update = () => {
      ticking = false;

      const y = window.scrollY;
      const vh = window.innerHeight;
      const max = document.documentElement.scrollHeight - vh;

      if (fill) {
        fill.style.transform = `scaleX(${max > 0 ? clamp(y / max) : 0})`;
      }
      nav?.classList.toggle('scrolled', y > 24);

      if (hero) {
        hero.style.setProperty('--hp', clamp(y / hero.offsetHeight).toFixed(3));
      }

      if (steps) {
        const r = steps.getBoundingClientRect();
        const p = clamp((vh * 0.72 - r.top) / r.height);
        steps.style.setProperty('--tp', p.toFixed(3));
        stepItems.forEach((el, i) =>
          el.classList.toggle('on', p >= (i + 0.2) / stepItems.length)
        );
      }

      // Scroll-spy: the last section whose top has passed the upper part of the screen
      let spy = 'top';
      for (const section of sections) {
        if (section.getBoundingClientRect().top <= vh * 0.35) {
          spy = section.id;
        }
      }
      if (spy !== current) {
        current = spy;
        this.zone.run(() => this.active.set(spy));
      }
    };

    const onScroll = () => {
      if (!ticking) {
        ticking = true;
        requestAnimationFrame(update);
      }
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    update();

    this.cleanups.push(() => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
    });
  }

  // ---------- Mouse: card spotlight and hero parallax ----------

  private setupPointer() {
    const root = this.host.nativeElement;
    const hero = root.querySelector<HTMLElement>('.hero');

    const move = (event: MouseEvent) => {
      const card =
        event.target instanceof Element ? event.target.closest<HTMLElement>('.spot') : null;
      if (card) {
        const r = card.getBoundingClientRect();
        card.style.setProperty('--sx', `${event.clientX - r.left}px`);
        card.style.setProperty('--sy', `${event.clientY - r.top}px`);
      }

      if (hero) {
        const r = hero.getBoundingClientRect();
        if (r.bottom > 0 && r.top < window.innerHeight) {
          const x = ((event.clientX - r.left) / r.width - 0.5) * 2;
          const y = ((event.clientY - r.top) / r.height - 0.5) * 2;
          hero.style.setProperty('--mx', x.toFixed(3));
          hero.style.setProperty('--my', y.toFixed(3));
        }
      }
    };

    const leave = () => {
      hero?.style.setProperty('--mx', '0');
      hero?.style.setProperty('--my', '0');
    };

    window.addEventListener('mousemove', move);
    hero?.addEventListener('mouseleave', leave);
    this.cleanups.push(() => {
      window.removeEventListener('mousemove', move);
      hero?.removeEventListener('mouseleave', leave);
    });
  }

  // ---------- The product tour changes by itself, unless the mouse is over it ----------

  private setupTabs() {
    const tour = this.host.nativeElement.querySelector<HTMLElement>('.tour');
    if (!tour || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    let paused = false;
    const enter = () => (paused = true);
    const leave = () => (paused = false);
    tour.addEventListener('mouseenter', enter);
    tour.addEventListener('mouseleave', leave);

    const timer = window.setInterval(() => {
      if (paused) {
        return;
      }
      const r = tour.getBoundingClientRect();
      const visible = r.top < window.innerHeight * 0.8 && r.bottom > window.innerHeight * 0.2;
      if (visible) {
        this.zone.run(() => this.tab.set((this.tab() + 1) % this.tabs.length));
      }
    }, 6500);

    this.cleanups.push(() => {
      window.clearInterval(timer);
      tour.removeEventListener('mouseenter', enter);
      tour.removeEventListener('mouseleave', leave);
    });
  }
}
