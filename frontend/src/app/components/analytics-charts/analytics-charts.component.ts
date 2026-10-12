import { DecimalPipe } from '@angular/common';
import {
  Component,
  ElementRef,
  NgZone,
  OnDestroy,
  computed,
  effect,
  input,
  output,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ActiveElement,
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  ChartEvent,
  DoughnutController,
  Legend,
  LinearScale,
  Tooltip
} from 'chart.js';
import { Analytics, ChartPoint, ExpenseService } from '../../services/expense.service';

Chart.register(
  DoughnutController,
  BarController,
  ArcElement,
  BarElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend
);

// What the parent should apply to the claims list when a slice or bar is clicked
export interface ChartSlice {
  status?: string;
  category?: string;
  department?: string;
  from?: string;
  to?: string;
}

type RangeKey = 'all' | 'month' | 'quarter' | 'year' | 'custom';

interface Theme {
  text: string;
  muted: string;
  grid: string;
  edge: string;
}

const CATEGORY_COLORS = [
  '#2f8a58',
  '#2b8fb5',
  '#f2a93b',
  '#8b6bd6',
  '#e0593f',
  '#14b8a6',
  '#ec4899',
  '#64748b'
];

const STATUS_COLORS: Record<string, string> = {
  PENDING: '#f2a93b',
  APPROVED: '#3a8f5f',
  REJECTED: '#e0593f',
  REIMBURSED: '#2b8fb5'
};

@Component({
  selector: 'app-analytics-charts',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './analytics-charts.component.html',
  styleUrl: './analytics-charts.component.css'
})
export class AnalyticsChartsComponent implements OnDestroy {
  heading = input('Spending Overview');
  showDepartment = input(false);
  refresh = input(0);
  slice = output<ChartSlice>();

  currency = '₹';
  statuses = ['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'REIMBURSED'];
  ranges: { key: RangeKey; label: string }[] = [
    { key: 'all', label: 'All time' },
    { key: 'month', label: 'This month' },
    { key: 'quarter', label: 'Last 3 months' },
    { key: 'year', label: 'This year' }
  ];

  // Filter fields (applied when the user presses Apply or picks a quick range)
  statusFilter = 'ALL';
  from = '';
  to = '';
  range = signal<RangeKey>('all');

  data = signal<Analytics | null>(null);
  loading = signal(false);
  error = signal('');
  dark = signal(this.isDark());

  average = computed(() => {
    const d = this.data();
    return d && d.claimCount > 0 ? Number(d.grandTotal) / d.claimCount : 0;
  });
  topCategory = computed<ChartPoint | null>(() => this.data()?.byCategory[0] ?? null);
  topDepartment = computed<ChartPoint | null>(() => this.data()?.byDepartment[0] ?? null);

  private catCanvas = viewChild<ElementRef<HTMLCanvasElement>>('catCanvas');
  private statusCanvas = viewChild<ElementRef<HTMLCanvasElement>>('statusCanvas');
  private monthCanvas = viewChild<ElementRef<HTMLCanvasElement>>('monthCanvas');
  private deptCanvas = viewChild<ElementRef<HTMLCanvasElement>>('deptCanvas');

  private charts: Chart[] = [];
  private observer: MutationObserver;
  private requestId = 0;
  private applied = { status: 'ALL', from: '', to: '' };

  private compact = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 });
  private full = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });

  constructor(
    private expenseService: ExpenseService,
    private zone: NgZone
  ) {
    // Redraw the graphs when the theme changes
    this.observer = new MutationObserver(() => this.dark.set(this.isDark()));
    this.observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme']
    });

    // Load on start, and again whenever the page asks for a refresh
    effect(() => {
      this.refresh();
      untracked(() => this.load());
    });

    // Draw whenever the data, the theme or the canvases change
    effect(() => {
      const data = this.data();
      const dark = this.dark();
      const cat = this.catCanvas()?.nativeElement;
      const status = this.statusCanvas()?.nativeElement;
      const month = this.monthCanvas()?.nativeElement;
      const dept = this.deptCanvas()?.nativeElement;
      untracked(() => this.draw(data, dark, cat, status, month, dept));
    });
  }

  ngOnDestroy() {
    this.observer.disconnect();
    this.destroyCharts();
  }

  setRange(key: RangeKey) {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth();

    if (key === 'all') {
      this.from = '';
      this.to = '';
    } else if (key === 'month') {
      this.from = this.iso(new Date(year, month, 1));
      this.to = this.iso(new Date(year, month + 1, 0));
    } else if (key === 'quarter') {
      this.from = this.iso(new Date(year, month - 2, 1));
      this.to = this.iso(new Date(year, month + 1, 0));
    } else if (key === 'year') {
      this.from = this.iso(new Date(year, 0, 1));
      this.to = this.iso(new Date(year, 11, 31));
    }

    this.range.set(key);
    this.apply();
  }

  onDateChange() {
    this.range.set('custom');
  }

  apply() {
    this.error.set('');

    if (this.from && this.to && this.from > this.to) {
      this.error.set('"From" date must not be after "To" date');
      return;
    }

    this.applied = { status: this.statusFilter, from: this.from, to: this.to };
    this.load();
  }

  private load() {
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set('');

    this.expenseService
      .getAnalytics(this.applied.status, this.applied.from, this.applied.to)
      .subscribe({
        next: (result) => {
          if (id !== this.requestId) {
            return;
          }
          this.data.set(result);
          this.loading.set(false);
        },
        error: () => {
          if (id !== this.requestId) {
            return;
          }
          this.loading.set(false);
          this.error.set('Could not load the charts. Please try again.');
        }
      });
  }

  // ---------- Graphs ----------

  private draw(
    data: Analytics | null,
    dark: boolean,
    cat?: HTMLCanvasElement,
    status?: HTMLCanvasElement,
    month?: HTMLCanvasElement,
    dept?: HTMLCanvasElement
  ) {
    this.destroyCharts();
    if (!data || data.claimCount === 0) {
      return;
    }

    const theme: Theme = {
      text: dark ? '#e5eee9' : '#1f2d2a',
      muted: dark ? '#9fb2a9' : '#5f6f6a',
      grid: dark ? 'rgba(255, 255, 255, 0.12)' : 'rgba(31, 77, 63, 0.12)',
      edge: dark ? 'rgba(16, 26, 34, 0.95)' : 'rgba(255, 255, 255, 0.95)'
    };

    // The animation frames run outside Angular, so the page is not re-checked on every frame
    this.zone.runOutsideAngular(() => {
      if (cat) {
        this.charts.push(
          this.doughnut(
            cat,
            data.byCategory,
            data.byCategory.map((_, i) => CATEGORY_COLORS[i % CATEGORY_COLORS.length]),
            theme,
            (label) => this.emit({ category: label })
          )
        );
      }
      if (status) {
        this.charts.push(
          this.doughnut(
            status,
            data.byStatus,
            data.byStatus.map((p) => STATUS_COLORS[p.label] ?? '#64748b'),
            theme,
            (label) => this.emit({ status: label })
          )
        );
      }
      if (month) {
        this.charts.push(
          this.bars(
            month,
            data.byMonth,
            data.byMonth.map((p) => this.monthLabel(p.label)),
            false,
            theme,
            '#2f8a58',
            (label) => this.emitMonth(label)
          )
        );
      }
      if (dept) {
        this.charts.push(
          this.bars(
            dept,
            data.byDepartment,
            data.byDepartment.map((p) => p.label),
            true,
            theme,
            '#2b8fb5',
            (label) => this.emit({ department: label })
          )
        );
      }
    });
  }

  private doughnut(
    canvas: HTMLCanvasElement,
    points: ChartPoint[],
    colors: string[],
    theme: Theme,
    onPick: (label: string) => void
  ): Chart {
    const total = points.reduce((sum, p) => sum + Number(p.total), 0);

    return new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: points.map((p) => p.label),
        datasets: [
          {
            data: points.map((p) => Number(p.total)),
            backgroundColor: colors,
            borderColor: theme.edge,
            borderWidth: 3,
            hoverOffset: 10
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '60%',
        animation: { duration: 900 },
        plugins: {
          legend: {
            position: 'bottom',
            labels: { color: theme.text, usePointStyle: true, boxWidth: 8, padding: 14 }
          },
          tooltip: {
            callbacks: {
              label: (ctx) =>
                this.tip(ctx.label, Number(ctx.raw), points[ctx.dataIndex]?.count ?? 0, total)
            }
          }
        },
        onClick: (_event: ChartEvent, elements: ActiveElement[]) => {
          const first = elements[0];
          if (first) {
            onPick(points[first.index].label);
          }
        },
        onHover: (event: ChartEvent, elements: ActiveElement[]) => this.cursor(event, elements)
      }
    });
  }

  private bars(
    canvas: HTMLCanvasElement,
    points: ChartPoint[],
    labels: string[],
    horizontal: boolean,
    theme: Theme,
    color: string,
    onPick: (label: string) => void
  ): Chart {
    const total = points.reduce((sum, p) => sum + Number(p.total), 0);
    const money = (value: string | number) => `₹${this.compact.format(Number(value))}`;

    const valueAxis = {
      beginAtZero: true,
      grid: { color: theme.grid },
      ticks: { color: theme.muted, callback: money }
    };
    const labelAxis = {
      grid: { display: false },
      ticks: { color: theme.text }
    };

    return new Chart(canvas, {
      type: 'bar',
      data: {
        labels,
        datasets: [
          {
            data: points.map((p) => Number(p.total)),
            backgroundColor: color,
            borderRadius: 8,
            maxBarThickness: 48
          }
        ]
      },
      options: {
        indexAxis: horizontal ? 'y' : 'x',
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 900 },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) =>
                this.tip(ctx.label, Number(ctx.raw), points[ctx.dataIndex]?.count ?? 0, total)
            }
          }
        },
        scales: horizontal ? { x: valueAxis, y: labelAxis } : { x: labelAxis, y: valueAxis },
        onClick: (_event: ChartEvent, elements: ActiveElement[]) => {
          const first = elements[0];
          if (first) {
            onPick(points[first.index].label);
          }
        },
        onHover: (event: ChartEvent, elements: ActiveElement[]) => this.cursor(event, elements)
      }
    });
  }

  private tip(label: string, value: number, count: number, total: number): string {
    const share = total > 0 ? `, ${Math.round((value / total) * 100)}%` : '';
    return `${label}: ₹${this.full.format(value)} (${count} ${count === 1 ? 'claim' : 'claims'}${share})`;
  }

  private cursor(event: ChartEvent, elements: ActiveElement[]) {
    const target = event.native?.target as HTMLElement | undefined;
    if (target) {
      target.style.cursor = elements.length ? 'pointer' : 'default';
    }
  }

  private destroyCharts() {
    for (const chart of this.charts) {
      chart.destroy();
    }
    this.charts = [];
  }

  // ---------- Click through to the claims list ----------

  // The graph's own filters (status and dates) are sent along, so the list matches the graph
  private emit(partial: ChartSlice) {
    const base: ChartSlice = {};
    if (this.applied.status !== 'ALL') {
      base.status = this.applied.status;
    }
    if (this.applied.from) {
      base.from = this.applied.from;
    }
    if (this.applied.to) {
      base.to = this.applied.to;
    }
    this.zone.run(() => this.slice.emit({ ...base, ...partial }));
  }

  private emitMonth(label: string) {
    const [year, month] = label.split('-').map(Number);
    const start = this.iso(new Date(year, month - 1, 1));
    const end = this.iso(new Date(year, month, 0));

    const from = this.applied.from && this.applied.from > start ? this.applied.from : start;
    const to = this.applied.to && this.applied.to < end ? this.applied.to : end;
    this.emit({ from, to });
  }

  // ---------- Helpers ----------

  private monthLabel(value: string): string {
    const [year, month] = value.split('-').map(Number);
    return new Date(year, month - 1, 1).toLocaleString('en-US', {
      month: 'short',
      year: '2-digit'
    });
  }

  private iso(d: Date): string {
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${month}-${day}`;
  }

  private isDark(): boolean {
    return document.documentElement.getAttribute('data-theme') === 'dark';
  }
}
