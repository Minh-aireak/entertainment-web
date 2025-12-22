import "./GlobalStyles.scss";
import { ThemeProvider, createTheme, CssBaseline } from "@mui/material";

const theme = createTheme({
  palette: {
    mode: "light",
    primary: { main: "#667eea" },
    secondary: { main: "#764ba2" },
    error: { main: "#ef4444" },
    success: { main: "#10b981" },
    background: {
      default: "#f8f9ff",
      paper: "#ffffff",
    },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: [
      "Roboto",
      "Helvetica",
      "Arial",
      "sans-serif",
    ].join(","),
    h6: { fontWeight: 600 },
    subtitle2: { fontWeight: 600 },
    body2: { lineHeight: 1.5 },
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: { textTransform: "none", borderRadius: 10 },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: { borderRadius: 16 },
      },
    },
    MuiAvatar: {
      styleOverrides: {
        root: { boxShadow: "0 2px 10px rgba(0,0,0,0.08)" },
      },
    },
  },
});

export default function GlobalStyles({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {children}
    </ThemeProvider>
  );
}
