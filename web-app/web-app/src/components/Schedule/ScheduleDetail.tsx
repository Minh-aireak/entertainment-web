import React from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
  Box,
  Chip,
  Paper,
} from "@mui/material";
import {
  AccessTime,
  CalendarToday,
  Description,
  Edit,
  Delete,
} from "@mui/icons-material";
import type { ScheduleResponse } from "../../InterfaceDataType/DataType";
import dayjs from "dayjs";

interface ScheduleDetailProps {
  schedule: ScheduleResponse | null;
  open: boolean;
  onCloseDetail: () => void;
  onEdit: (scheduleId: string) => void;
  onDelete: (scheduleId: string) => void;
}

const getStatusColor = (status: string) => {
  switch (status) {
    case "Up coming":
      return "primary";
    case "On going":
      return "success";
    case "Completed":
      return "default";
  }
};

const getStatusText = (status: string) => {
  switch (status) {
    case "Up coming":
      return "Up coming";
    case "On going":
      return "On going";
    case "Completed":
      return "Completed";
    default:
  }
};

const ScheduleDetail: React.FC<ScheduleDetailProps> = ({
  schedule,
  open,
  onCloseDetail,
  onEdit,
  onDelete,
}) => {
  if (!schedule) return null;

  return (
    <Dialog
      open={open}
      onClose={onCloseDetail}
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
          Schedule Details
        </Typography>
      </DialogTitle>
      <DialogContent sx={{ pt: 0 }}>
        <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "flex-start",
              mb: 2,
            }}
          >
            <Typography variant="h4" component="h1" sx={{ fontWeight: 700 }}>
              {schedule.title}
            </Typography>
            <Chip
              label={getStatusText(schedule.status)}
              color={getStatusColor(schedule.status) as any}
              size="medium"
              sx={{ ml: 2, fontSize: 13 }}
            />
          </Box>

          <Box>
            <Paper sx={{ pb: 3, mb: 2, pl: 3, pr: 3 }}>
              <Typography
                variant="h6"
                sx={{
                  mb: 2,
                  display: "flex",
                  alignItems: "center",
                  fontSize: 20,
                }}
              >
                <Description sx={{ mr: 1, fontSize: 30 }} />
                Content
              </Typography>
              <Typography
                variant="body1"
                color="text.secondary"
                sx={{ fontSize: 15, fontWeight: "bold" }}
              >
                {schedule.content}
              </Typography>
            </Paper>
          </Box>

          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: { xs: "1fr", md: "repeat(2, 1fr)" },
              gap: 3,
            }}
          >
            <Paper sx={{ p: 3, height: "100%" }}>
              <Typography
                variant="h6"
                sx={{
                  mb: 2,
                  display: "flex",
                  alignItems: "center",
                  fontSize: 20,
                }}
              >
                <AccessTime sx={{ mr: 1, fontSize: 30 }} />
                Time
              </Typography>
              <Box sx={{ mb: 2 }}>
                <Typography
                  variant="body1"
                  color="text.secondary"
                  sx={{ mb: 0.5, fontSize: 13, fontWeight: "bold" }}
                >
                  Start:
                </Typography>
                <Typography
                  variant="body1"
                  sx={{ fontSize: 13, fontWeight: "bold" }}
                >
                  {dayjs(schedule.startTime).format("DD/MM/YYYY HH:mm")}
                </Typography>
              </Box>
              <Box>
                <Typography
                  variant="body1"
                  color="text.secondary"
                  sx={{ mb: 0.5, fontSize: 13, fontWeight: "bold" }}
                >
                  End:
                </Typography>
                <Typography
                  variant="body1"
                  sx={{ fontSize: 13, fontWeight: "bold" }}
                >
                  {dayjs(schedule.endTime).format("DD/MM/YYYY HH:mm")}
                </Typography>
              </Box>
            </Paper>

            <Paper sx={{ p: 3, height: "100%" }}>
              <Typography
                variant="h6"
                sx={{
                  mb: 2,
                  display: "flex",
                  alignItems: "center",
                  fontSize: 20,
                }}
              >
                <CalendarToday sx={{ mr: 1, fontSize: 30 }} />
                Other information
              </Typography>
              <Box sx={{ mb: 2 }}>
                <Typography
                  variant="body1"
                  color="text.secondary"
                  sx={{ mb: 0.5, fontSize: 13, fontWeight: "bold" }}
                >
                  Created:
                </Typography>
                <Typography
                  variant="body1"
                  sx={{ fontSize: 13, fontWeight: "bold" }}
                >
                  {schedule.createdDate}
                </Typography>
              </Box>
              <Box>
                <Typography
                  variant="body1"
                  color="text.secondary"
                  sx={{ mb: 0.5, fontSize: 13, fontWeight: "bold" }}
                >
                  Updated at:
                </Typography>
                <Typography
                  variant="body1"
                  sx={{ fontSize: 13, fontWeight: "bold" }}
                >
                  {dayjs(schedule.modifiedDate).format("DD/MM/YYYY HH:mm")}
                </Typography>
              </Box>
            </Paper>
          </Box>
        </Box>
      </DialogContent>
      <DialogActions sx={{ p: 3, pt: 0 }}>
        <Button onClick={onCloseDetail} variant="outlined">
          Close
        </Button>
        <Button
          onClick={() => onDelete(schedule.id)}
          sx={{ backgroundColor: "error.main", color: "white" }}
          startIcon={<Delete />}
        >
          Delete
        </Button>
        <Button
          onClick={() => onEdit(schedule.id)}
          variant="contained"
          startIcon={<Edit />}
        >
          Edit
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ScheduleDetail;
