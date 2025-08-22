import Snackbar, { type SnackbarProps } from "@mui/material/Snackbar";
import Alert, { type AlertProps } from "@mui/material/Alert";

interface CustomAlertSnackbarProps extends Omit<SnackbarProps, "onClose"> {
  message: React.ReactNode;
  severity?: AlertProps["severity"];
  onClose?: () => void;
}

export const CustomAlertSnackbar: React.FC<CustomAlertSnackbarProps> = ({
  autoHideDuration = 5000,
  anchorOrigin = { vertical: "bottom", horizontal: "right" },
  message,
  severity,
  onClose,
  open,
  ...snackbarProps
}) => {
  const handleClose: SnackbarProps["onClose"] = (event, reason) => {
    if (reason === "clickaway") return;
    onClose?.();
  };

  const handleAlertClose = (event?: React.SyntheticEvent) => {
    onClose?.();
  };

  return (
    <Snackbar
      open={open}
      autoHideDuration={autoHideDuration}
      anchorOrigin={anchorOrigin}
      onClose={handleClose}
      {...snackbarProps}
    >
      <Alert
        onClose={handleAlertClose}
        severity={severity}
        variant="filled"
        sx={{ width: "100%" }}
      >
        {message}
      </Alert>
    </Snackbar>
  );
};
