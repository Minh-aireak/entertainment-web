/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: "#00A84E",
        "primary-light": "#00C853",
        "primary-dark": "#008F41",
        secondary: "#141414",
        "secondary-light": "#1A1A1A",
        "secondary-dark": "#0A0A0A",
        error: "#FF4757",
        success: "#00A84E",
        warning: "#FFA502",
        dark: {
          bg: "#0A0A0A",
          paper: "#141414",
          border: "#262626",
          text: "#FFFFFF",
        },
      },
      backgroundColor: {
        dark: "#0A0A0A",
        "dark-paper": "#141414",
      },
      borderColor: {
        "dark-primary": "#262626",
      },
      boxShadow: {
        "glow": "0 0 20px rgba(0, 168, 78, 0.2)",
      },
    },
  },
  plugins: [],
}


