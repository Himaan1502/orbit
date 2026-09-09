'use client';

import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

const CYAN = 'var(--accent-cyan)';
const VIOLET = 'var(--accent-violet)';
const GRID = 'var(--border)';
const AXIS = 'var(--text-secondary)';

const axisProps = {
  stroke: AXIS,
  tick: { fill: AXIS, fontSize: 10, fontFamily: 'ui-monospace, monospace' },
  tickLine: false,
  axisLine: { stroke: GRID },
} as const;

function tooltipStyle() {
  return {
    contentStyle: {
      background: 'var(--bg-surface-raised)',
      border: '1px solid var(--border)',
      borderRadius: 8,
      fontSize: 11,
    },
    labelStyle: { color: 'var(--text-secondary)' },
    itemStyle: { color: 'var(--text-primary)' },
  };
}

export interface Series {
  key: string;
  label: string;
  /** "primary" cyan, "secondary" violet, else neutral grey. */
  tone?: 'primary' | 'secondary' | 'neutral';
}

const toneColor = (tone?: string) =>
  tone === 'primary' ? CYAN : tone === 'secondary' ? VIOLET : AXIS;

/** Line chart for change over time (Design System §108) — 1.5px lines, dashed grid. */
export function TrendChart({
  data,
  xKey,
  series,
  height = 200,
  yDomain,
}: {
  data: Record<string, unknown>[];
  xKey: string;
  series: Series[];
  height?: number;
  yDomain?: [number, number];
}) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 8, right: 12, bottom: 4, left: -12 }}>
        <CartesianGrid stroke={GRID} strokeDasharray="2 4" vertical={false} />
        <XAxis dataKey={xKey} {...axisProps} interval="preserveStartEnd" minTickGap={24} />
        <YAxis {...axisProps} domain={yDomain} width={44} />
        <Tooltip {...tooltipStyle()} />
        {series.map((s) => (
          <Line
            key={s.key}
            type="monotone"
            dataKey={s.key}
            name={s.label}
            stroke={toneColor(s.tone)}
            strokeWidth={1.5}
            dot={false}
            activeDot={{ r: 3 }}
            isAnimationActive={false}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}

/** Bar chart for comparisons (Design System §109). */
export function CompareChart({
  data,
  xKey,
  series,
  height = 200,
}: {
  data: Record<string, unknown>[];
  xKey: string;
  series: Series[];
  height?: number;
}) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 12, bottom: 4, left: -12 }}>
        <CartesianGrid stroke={GRID} strokeDasharray="2 4" vertical={false} />
        <XAxis dataKey={xKey} {...axisProps} interval="preserveStartEnd" minTickGap={24} />
        <YAxis {...axisProps} width={44} allowDecimals={false} />
        <Tooltip {...tooltipStyle()} cursor={{ fill: 'var(--bg-hover)' }} />
        {series.map((s) => (
          <Bar
            key={s.key}
            dataKey={s.key}
            name={s.label}
            fill={toneColor(s.tone)}
            radius={[2, 2, 0, 0]}
            isAnimationActive={false}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Tiny inline sparkline — a single hero metric (Design System §108, area fill allowed). */
export function Sparkline({
  data,
  yKey,
  height = 40,
}: {
  data: Record<string, unknown>[];
  yKey: string;
  height?: number;
}) {
  if (data.length < 2) {
    return (
      <span className="font-mono text-[11px] text-[var(--text-secondary)]">not enough history</span>
    );
  }
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 2, right: 0, bottom: 2, left: 0 }}>
        <defs>
          <linearGradient id="spark" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={CYAN} stopOpacity={0.35} />
            <stop offset="100%" stopColor={CYAN} stopOpacity={0} />
          </linearGradient>
        </defs>
        <Area
          type="monotone"
          dataKey={yKey}
          stroke={CYAN}
          strokeWidth={1.5}
          fill="url(#spark)"
          isAnimationActive={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
