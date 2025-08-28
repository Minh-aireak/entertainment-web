import {
  Box,
  Button,
  Card,
  CardContent,
  Divider,
  TextField,
  Typography,
  Snackbar,
  Alert,
} from "@mui/material";
import GoogleIcon from "@mui/icons-material/Google";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { logIn, isAuthenticated } from "../../features/hooks/useAuthApi";
import { OAuthConfig } from "../../configurations/configuration";

export default function Login() {
  type SnackbarCloseReason = "timeout" | "clickaway" | "escapeKeyDown";

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [snackBarOpen, setSnackBarOpen] = useState(false);
  const [snackBarMessage, setSnackBarMessage] = useState("");
  const navigate = useNavigate();

  const handleCloseSnackBar = (
    event: Event | React.SyntheticEvent<any, Event>,
    reason: SnackbarCloseReason
  ) => {
    if (reason === "clickaway") {
      return;
    }

    setSnackBarOpen(false);
  };

  const handleLoginWithGG = () => {
    const callbackUrl = OAuthConfig.redirectUri;
    const authUrl = OAuthConfig.authUri;
    const googleClientId = OAuthConfig.clientId;

    const targetUrl = `${authUrl}?redirect_uri=${encodeURIComponent(
      callbackUrl
    )}&response_type=code&client_id=${googleClientId}&scope=openid%20email%20profile`;

    console.log(targetUrl);

    window.location.href = targetUrl;
  };

  useEffect(() => {
    if (isAuthenticated()) {
      navigate("/");
    }
  }, [navigate]);

  const handleSubmit = async (
    event: Event | React.SyntheticEvent<any, Event>
  ) => {
    event.preventDefault();

    try {
      await logIn(username, password);
      navigate("/");
    } catch (error: unknown) {
      const errorResponse = (error as any).response.data;
      setSnackBarMessage(errorResponse.message);
      setSnackBarOpen(true);
    }
  };

  return (
    <div className="wrapper">
      <Snackbar
        open={snackBarOpen}
        onClose={handleCloseSnackBar}
        autoHideDuration={5000}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <Alert severity="error" variant="filled" sx={{ width: "100%" }}>
          {snackBarMessage}
        </Alert>
      </Snackbar>
      <Box
        display="flex"
        flexDirection="column"
        alignItems="center"
        justifyContent="center"
        height="100vh"
        width={"150%"}
      >
        <Card
          sx={{
            width: 400,
            height: 500,
            boxShadow: 3,
            borderRadius: 3,
            padding: 4,
          }}
        >
          <CardContent>
            <Typography
              variant="h4"
              component="h1"
              gutterBottom
              textAlign={"center"}
              marginTop={"15px"}
            >
              LOGIN
            </Typography>
            <Box
              component="form"
              display="flex"
              flexDirection="column"
              alignItems="center"
              justifyContent="center"
              width="100%"
              onSubmit={handleSubmit}
            >
              <TextField
                label="Username"
                variant="outlined"
                fullWidth
                margin="normal"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
              <TextField
                label="Password"
                type="password"
                variant="outlined"
                fullWidth
                margin="normal"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <Button
                type="submit"
                variant="contained"
                color="primary"
                size="large"
                onClick={handleSubmit}
                fullWidth
                sx={{
                  mt: "25px",
                  mb: "15px",
                }}
              >
                Login
              </Button>
              <Divider></Divider>
            </Box>

            <Box display="flex" flexDirection="column" width="100%" gap="15px">
              <Button
                type="button"
                variant="contained"
                color="secondary"
                size="large"
                onClick={handleLoginWithGG}
                fullWidth
                sx={{ gap: "15px" }}
              >
                <GoogleIcon />
                Continue with Google
              </Button>
              <Button
                type="submit"
                variant="contained"
                color="success"
                size="large"
              >
                Create an account
              </Button>
            </Box>
          </CardContent>
        </Card>
      </Box>
    </div>
  );
}