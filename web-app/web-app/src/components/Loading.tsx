import { Box, CircularProgress, Typography } from "@mui/material";

interface LoadingProp {
  loading: boolean;
}
export const Loading: React.FC<LoadingProp> = (loading) => {
  return (
    loading && (
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          gap: "30px",
          justifyContent: "center",
          alignItems: "center",
          height: "100vh",
        }}
      >
        <CircularProgress></CircularProgress>
        <Typography>Loading ...</Typography>
      </Box>
    )
  );
};
