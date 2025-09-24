import React from "react";
import {
  Dialog,
  DialogTitle,
  DialogActions,
  DialogContent,
  Button,
  Typography,
  IconButton,
} from "@mui/material";
import { Close, Delete, WarningAmber } from "@mui/icons-material";

interface ScheduleDeletelProps {
  open: boolean;
  scheduleId: string;
  type: string;
  onClose: () => void;
  onDelete: (scheduleId: string, type: string) => void;
}

const ScheduleDelete: React.FC<ScheduleDeletelProps> = ({
  open,
  scheduleId,
  onClose,
  type,
  onDelete,
}) => {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      slotProps={{
        root: {
          sx: {
            borderRadius: 2,
            maxHeight: "90vh",
          },
        },
      }}
    >
      <DialogTitle
        sx={{
          px: 3,
          pt: 2,
          pb: 1,
          display: "flex",
          alignItems: "center",
        }}
      >
        <WarningAmber sx={{ color: "error.main", fontSize: 28 }} />
        <Typography variant="h6" fontWeight="bold" fontSize={23}>
          Confirm Deletion
        </Typography>
        <IconButton
          aria-label="close"
          onClick={onClose}
          sx={{ position: "absolute", right: 8, top: 8 }}
        >
          <Close />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ px: 3, pb: 3 }}>
        <Typography
          variant="body1"
          gutterBottom
          fontSize={17}
          fontWeight="bold"
        >
          Are you sure you want to delete this schedule?
        </Typography>
        <Typography variant="body2" fontSize={17}>
          This action cannot be undone.
        </Typography>
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={onClose} variant="outlined" sx={{ borderRadius: 1 }}>
          Cancel
        </Button>

        <Button
          onClick={() => onDelete && onDelete(scheduleId, type)}
          variant="contained"
          color="error"
          startIcon={<Delete />}
          sx={{ borderRadius: 1 }}
        >
          Delete
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ScheduleDelete;
