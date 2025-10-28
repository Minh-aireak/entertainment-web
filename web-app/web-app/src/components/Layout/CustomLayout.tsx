import Header from "../Header/Header";
import Sidebar from "../Sidebar/Sidebar";
import { Box } from "@mui/material";

export const DefaultLayout = ({ children }: { children: React.ReactNode }) => {
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        minHeight: "100vh",
        background: "linear-gradient(180deg, #f8f9ff 0%, #ffffff 100%)",
      }}
    >
      <Header />
      <Box
        sx={{
          display: "flex",
          flex: 1,
          overflow: "hidden",
        }}
      >
        <Sidebar />
        <Box
          component="main"
          sx={{
            flex: 1,
            overflow: "auto",
            background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
            minHeight: "calc(100vh - 64px)",
            padding: 3,
            paddingTop: '64px',
            position: "relative",
            "&::before": {
              content: '""',
              position: "absolute",
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              background: `
                radial-gradient(circle at 20% 80%, rgba(102, 126, 234, 0.1) 0%, transparent 50%),
                radial-gradient(circle at 80% 20%, rgba(118, 75, 162, 0.1) 0%, transparent 50%),
                radial-gradient(circle at 40% 40%, rgba(79, 172, 254, 0.1) 0%, transparent 50%)
              `,
              pointerEvents: "none",
              zIndex: 0,
            },
            "& > *": {
              position: "relative",
              zIndex: 1,
            },
          }}
        >
          {children}
        </Box>
      </Box>
    </Box>
  );
};

export const HeaderContentLayout = ({
  children,
}: {
  children: React.ReactNode;
}) => {
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        minHeight: "100vh",
        background: "linear-gradient(180deg, #f8f9ff 0%, #ffffff 100%)",
      }}
    >
      <Header />
      <Box
        component="main"
        sx={{
          flex: 1,
          maxWidth: "1200px",
          width: "100%",
          margin: "0 auto",
          padding: 3,
          paddingTop: '64px',
          background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
          minHeight: "calc(100vh - 64px)",
          position: "relative",
          "&::before": {
            content: '""',
            position: "absolute",
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: `
              radial-gradient(circle at 20% 80%, rgba(102, 126, 234, 0.1) 0%, transparent 50%),
              radial-gradient(circle at 80% 20%, rgba(118, 75, 162, 0.1) 0%, transparent 50%)
            `,
            pointerEvents: "none",
            zIndex: 0,
          },
          "& > *": {
            position: "relative",
            zIndex: 1,
          },
        }}
      >
        {children}
      </Box>
    </Box>
  );
};

export const ContentLayout = ({ children }: { children: React.ReactNode }) => {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
      }}
    >
      <main
        style={{
          width: "var(--default-layout-width)",
        }}
      >
        {children}
      </main>
    </div>
  );
};

export const LoginLayout = ({ children }: { children: React.ReactNode }) => {
  const style: React.CSSProperties = {
    position: "relative",
    background: `
      linear-gradient(135deg, #667eea 0%, #764ba2 25%, #f093fb 50%, #f5576c 75%, #4facfe 100%),
      radial-gradient(circle at 20% 80%, rgba(120, 119, 198, 0.3) 0%, transparent 50%),
      radial-gradient(circle at 80% 20%, rgba(255, 119, 198, 0.3) 0%, transparent 50%),
      radial-gradient(circle at 40% 40%, rgba(120, 219, 255, 0.3) 0%, transparent 50%)
    `,
    backgroundSize: "400% 400%, 100% 100%, 100% 100%, 100% 100%",
    backgroundBlendMode: "normal, overlay, overlay, overlay",
    animation: "gradientShift 20s ease infinite",
    width: "100%",
    height: "100vh",
    overflow: "hidden",
  };

  // Add keyframes animation via a style tag
  const keyframes = `
    @keyframes gradientShift {
      0% { background-position: 0% 50%, 0% 0%, 0% 0%, 0% 0%; }
      25% { background-position: 100% 0%, 25% 25%, 75% 25%, 25% 75%; }
      50% { background-position: 100% 100%, 50% 50%, 50% 50%, 50% 50%; }
      75% { background-position: 0% 100%, 75% 75%, 25% 75%, 75% 25%; }
      100% { background-position: 0% 50%, 100% 100%, 100% 100%, 100% 100%; }
    }
  `;

  return (
    <>
      <style>{keyframes}</style>
      <main style={style}>{children}</main>
    </>
  );
};
