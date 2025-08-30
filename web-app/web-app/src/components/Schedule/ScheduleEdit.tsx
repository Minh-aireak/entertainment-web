import React, { useEffect, useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Paper,
  TextField,
} from "@mui/material";
import { Save } from "@mui/icons-material";
import type {
  PostDataUpdate,
  ScheduleResponse,
} from "../../InterfaceDataType/DataType";

interface ScheduleEditProps {
  schedule: ScheduleResponse | null;
  open: boolean;
  onClose: () => void;
  onSave: (scheduleId: string, scheduleData: PostDataUpdate) => void;
}

const ScheduleEdit: React.FC<ScheduleEditProps> = ({
  open,
  schedule,
  onClose,
  onSave,
}) => {
  const [scheduleData, setScheduleData] = useState<PostDataUpdate>({
    title: "",
    content: "",
    startTime: new Date(),
    endTime: new Date(),
  });
  const handleChangData = (field: keyof PostDataUpdate, value: string) => {
    setScheduleData((prev) => ({
      ...prev,
      [field]: value,
    }));
  };
  useEffect(() => {
    if (schedule) {
      setScheduleData({
        title: schedule.title ?? "",
        content: schedule.content ?? "",
        startTime: schedule.startTime ?? new Date(),
        endTime: schedule.endTime ?? new Date(),
      });
    }
  }, [schedule]);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
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
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          pb: 1,
        }}
      >
        <Typography variant="h5" component="div" sx={{ fontWeight: 600 }}>
          Schedule Edits
        </Typography>
      </DialogTitle>
      <DialogContent>
        <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              flexDirection: "column",
              gap: 1,
              alignItems: "flex-start",
            }}
          >
            <Paper sx={{ p: 3, height: "100%", mt: 1, width: "100%" }}>
              <TextField
                autoFocus
                fullWidth
                value={scheduleData.title}
                onChange={(e) => handleChangData("title", e.target.value)}
                label="Title"
                slotProps={{
                  inputLabel: {
                    shrink: true,
                    sx: {
                      fontSize: "1.25rem",
                      fontWeight: 700,
                      color: "#333",
                    },
                  },
                }}
              />
            </Paper>
            <Paper sx={{ p: 3, height: "100%", mt: 4, width: "100%" }}>
              <TextField
                autoFocus
                fullWidth
                value={scheduleData.content}
                onChange={(e) => handleChangData("content", e.target.value)}
                label="Content"
                slotProps={{
                  inputLabel: {
                    shrink: true,
                    sx: {
                      fontSize: "1.25rem",
                      fontWeight: 700,
                      color: "#333",
                    },
                  },
                }}
              />
            </Paper>
            <Paper sx={{ p: 3, height: "100%", mt: 4, width: "100%" }}>
              <TextField
                autoFocus
                fullWidth
                type="datetime-local"
                value={scheduleData.startTime}
                onChange={(e) => handleChangData("startTime", e.target.value)}
                label="Start time"
                slotProps={{
                  inputLabel: {
                    shrink: true,
                    sx: {
                      fontSize: "1.25rem",
                      fontWeight: 700,
                      color: "#333",
                    },
                  },
                }}
              />
            </Paper>
            <Paper sx={{ p: 3, height: "100%", mt: 4, width: "100%" }}>
              <TextField
                autoFocus
                fullWidth
                type="datetime-local"
                value={scheduleData.endTime}
                onChange={(e) => handleChangData("endTime", e.target.value)}
                label="End time"
                slotProps={{
                  inputLabel: {
                    shrink: true,
                    sx: {
                      fontSize: "1.25rem",
                      fontWeight: 700,
                      color: "#333",
                    },
                  },
                }}
              />
            </Paper>
          </Box>
        </Box>
      </DialogContent>
      <DialogActions sx={{ p: 3, pt: 0 }}>
        <Button onClick={onClose} variant="outlined">
          Close
        </Button>
        <Button
          onClick={() => onSave(schedule?.id ?? "", scheduleData)}
          variant="contained"
          startIcon={<Save />}
        >
          Save changes
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ScheduleEdit;
