/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        botanical: {
          dark: '#111613',
          darkCard: '#1a241e',
          light: '#F6F4EE',
          lightCard: '#ffffff',
          olive: '#7E9A68',
          pistachio: '#b4cf9b',
          sage: '#4D6B3C',
          deepOlive: '#365226',
          amberDark: '#E9B44C',
          amberLight: '#D48B28',
        },
      },
    },
  },
  plugins: [],
};
