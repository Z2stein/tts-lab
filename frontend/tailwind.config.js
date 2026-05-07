/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      colors: {
        studio: {
          bg: '#120f14',
          shell: '#17131a',
          panel: '#1a161d',
          panelSoft: '#211b23',
          field: '#100d12',
          line: '#392d36',
          lineStrong: '#4b3b45',
          text: '#f8efe5',
          muted: '#cbbdb0',
          accent: '#f0ad5d',
          accentSoft: '#f4c78e',
          danger: '#ffd7d7',
          dangerBg: '#3a1618',
          dangerLine: '#834047',
          success: '#8fe7bd'
        }
      },
      fontFamily: {
        sans: ['Arial', 'sans-serif']
      }
    }
  },
  plugins: []
};
