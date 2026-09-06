import type { Config } from 'tailwindcss';

const config: Config = {
  content: [
    './src/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        orbit: {
          base: 'var(--bg-base)',
          surface: 'var(--bg-surface)',
          'surface-raised': 'var(--bg-surface-raised)',
          hover: 'var(--bg-hover)',
          border: 'var(--border)',
          'text-primary': 'var(--text-primary)',
          'text-secondary': 'var(--text-secondary)',
          violet: 'var(--accent-violet)',
          cyan: 'var(--accent-cyan)',
          success: 'var(--color-success)',
          warning: 'var(--color-warning)',
          danger: 'var(--color-danger)',
        },
      },
      fontFamily: {
        sans: ['var(--font-geist-sans)', 'system-ui', 'sans-serif'],
        mono: ['var(--font-geist-mono)', 'monospace'],
      },
      borderRadius: {
        card: '12px',
        button: '8px',
        pill: '9999px',
      },
      spacing: {
        'sidebar-expanded': '240px',
        'sidebar-collapsed': '64px',
        topbar: '64px',
      },
      boxShadow: {
        overlay: 'var(--shadow-overlay)',
        'focus-glow': 'var(--focus-glow)',
      },
      animation: {
        'fade-in': 'fadeIn 200ms ease-out',
        'slide-up': 'slideUp 200ms ease-out',
      },
      keyframes: {
        fadeIn: {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        slideUp: {
          from: { opacity: '0', transform: 'translateY(10px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
      },
    },
  },
  plugins: [],
};

export default config;
