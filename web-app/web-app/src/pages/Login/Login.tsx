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
import axios from "axios";
import styles from "./Login.module.scss";

export default function Login() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [snackBarOpen, setSnackBarOpen] = useState(false);
  const [snackBarMessage, setSnackBarMessage] = useState("");
  const navigate = useNavigate();

  const handleCloseSnackBar = () => {
    setSnackBarOpen(false);
  };

  const handleLoginWithGG = () => {
    const callbackUrl = OAuthConfig.redirectUri;
    const authUrl = OAuthConfig.authUri;
    const googleClientId = OAuthConfig.clientId;

    const targetUrl = `${authUrl}?redirect_uri=${encodeURIComponent(
      callbackUrl
    )}&response_type=code&client_id=${googleClientId}&scope=openid%20email%20profile`;

    window.location.href = targetUrl;
  };

  useEffect(() => {
    if (isAuthenticated()) {
      navigate("/");
    }
  }, [navigate]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();

    try {
      await logIn(username, password);
      navigate("/");
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackBarMessage(messageToShow);
      setSnackBarOpen(true);
    }
  };

  return (
    <div className={styles.loginContainer}>
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
        sx={{
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          minHeight: "100vh",
          padding: 2,
        }}
      >
        <Card
          className={styles.glassCard}
          sx={{
            maxWidth: 450,
            width: "100%",
            background: "rgba(255, 255, 255, 0.15)",
            backdropFilter: "blur(25px)",
            border: "1px solid rgba(255, 255, 255, 0.25)",
            borderRadius: 4,
            boxShadow: "0 20px 60px rgba(0, 0, 0, 0.4)",
          }}
        >
          <CardContent sx={{ padding: "40px 35px" }}>
            <Box sx={{ textAlign: "center", mb: 4 }}>
              <Typography
                variant="h3"
                component="h1"
                className={styles.gradientText}
              >
                Welcome Back
              </Typography>
              <Typography
                variant="body1"
                sx={{
                  color: "rgba(255, 255, 255, 0.85)",
                  fontSize: "1.1rem",
                  mt: 1,
                }}
              >
                Sign in to continue your journey
              </Typography>
            </Box>

            <Box
              component="form"
              onSubmit={handleSubmit}
              sx={{ display: "flex", flexDirection: "column", gap: 2 }}
            >
              <TextField
                label="Username"
                variant="outlined"
                fullWidth
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className={styles.inputField}
                sx={{
                  "& .MuiOutlinedInput-root": {
                    background: "rgba(255, 255, 255, 0.12)",
                    backdropFilter: "blur(15px)",
                    borderRadius: 3,
                    "& fieldset": {
                      border: "1px solid rgba(255, 255, 255, 0.2)",
                    },
                    "&.Mui-focused fieldset": {
                      border: "1px solid rgba(255, 255, 255, 0.4)",
                    },
                  },
                }}
              />

              <TextField
                label="Password"
                type="password"
                variant="outlined"
                fullWidth
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={styles.inputField}
                sx={{
                  "& .MuiOutlinedInput-root": {
                    background: "rgba(255, 255, 255, 0.12)",
                    backdropFilter: "blur(15px)",
                    borderRadius: 3,
                    "& fieldset": {
                      border: "1px solid rgba(255, 255, 255, 0.2)",
                    },
                    "&.Mui-focused fieldset": {
                      border: "1px solid rgba(255, 255, 255, 0.4)",
                    },
                  },
                }}
              />
              <Button
                type="submit"
                variant="contained"
                fullWidth
                className={`${styles.primaryButton} ${styles.rippleEffect}`}
                sx={{
                  mt: 3,
                  mb: 3,
                  height: "56px",
                  background:
                    "linear-gradient(135deg, #667eea 0%, #764ba2 50%, #f093fb 100%)",
                  borderRadius: 3,
                  fontWeight: 600,
                  textTransform: "none",
                  fontSize: "1.15rem",
                }}
              >
                Sign In
              </Button>
            </Box>

            <Divider sx={{ my: 3, borderColor: "rgba(255, 255, 255, 0.25)" }}>
              <Typography sx={{ color: "rgba(255, 255, 255, 0.8)", px: 2 }}>
                OR
              </Typography>
            </Divider>

            <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
              <Button
                fullWidth
                variant="outlined"
                startIcon={<GoogleIcon />}
                onClick={handleLoginWithGG}
                className={`${styles.googleButton} ${styles.rippleEffect}`}
                sx={{
                  height: "52px",
                  background: "rgba(255, 255, 255, 0.12)",
                  border: "1px solid rgba(255, 255, 255, 0.25)",
                  borderRadius: 3,
                  color: "white",
                  fontWeight: 600,
                  textTransform: "none",
                }}
              >
                Continue with Google
              </Button>

              <Button
                fullWidth
                variant="text"
                onClick={() => navigate("/register")}
                className={styles.textButton}
                sx={{
                  height: "48px",
                  color: "rgba(255, 255, 255, 0.85)",
                  fontWeight: 500,
                  textTransform: "none",
                  borderRadius: 3,
                }}
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
