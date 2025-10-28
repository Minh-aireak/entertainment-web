import { Box, CircularProgress, Typography } from "@mui/material";

interface LoadingProp {
  loading: boolean;
}
export const Loading: React.FC<LoadingProp> = ({ loading }) => {
  if (!loading) return null;
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: "30px",
        justifyContent: "center",
        alignItems: "center",
        height: "100vh",
        zIndex: "999999",
      }}
    >
      <CircularProgress />
      <Typography>Loading ...</Typography>
    </Box>
  );
};
