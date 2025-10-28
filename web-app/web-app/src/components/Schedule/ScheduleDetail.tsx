import React, { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Typography,
  Box,
  Chip,
  Paper,
  Avatar,
} from "@mui/material";
import {
  AccessTime,
  CalendarToday,
  Description,
  Edit,
  Delete,
  LocationOn,
  Flag,
} from "@mui/icons-material";
import type {
  ScheduleResponse,
  DataWeatherResponse,
} from "../../InterfaceDataType/DataType";
import dayjs from "dayjs";
import ForecastList from "../ForecastCard/ForecastList";

interface ScheduleDetailProps {
  schedule: ScheduleResponse | null;
  open: boolean;
  onCloseDetail: () => void;
  onEdit: (scheduleId: string) => void;
  onDelete: (scheduleId: string) => void;
  weatherStart?: DataWeatherResponse;
  weatherEnd?: DataWeatherResponse;
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
  weatherStart,
  weatherEnd,
}) => {
  const mapOpen = schedule?.postType === "TRAVEL_ITINERARY" ? true : false;
  if (!schedule) return null;

  return (
    <Dialog
      open={open}
      onClose={onCloseDetail}
      fullScreen
      sx={{
        "& .MuiDialog-paper": {
          display: "flex",
          flexDirection: "row",
          width: mapOpen ? "100%" : "800px",
        },
      }}
    >
      <Box
        sx={{
          flex: 7,
          p: 2,
          overflowY: "auto",
        }}
      >
        <DialogTitle>Schedule Details</DialogTitle>
        <DialogContent
          sx={{
            pt: 2,
            display: "flex",
            flexDirection: "column",
            gap: 2,
          }}
        >
          {/* User info section */}
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              mb: 2,
            }}
          >
            <Avatar
              src={schedule.avatar}
              alt={schedule.displayName}
              sx={{ width: 40, height: 40, mr: 2 }}
            />
            <Typography
              variant="h6"
              color="text.secondary"
              sx={{ fontWeight: 500 }}
            >
              Created by {schedule.displayName}
            </Typography>
          </Box>

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

          {schedule.postType === "TRAVEL_ITINERARY" && (
            <>
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
                  <LocationOn sx={{ mr: 1, fontSize: 30 }} />
                  Start position: {weatherStart?.city?.name ?? ""}
                </Typography>
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
                  <Flag sx={{ mr: 1, fontSize: 30 }} />
                  End position: {weatherEnd?.city?.name ?? ""}
                </Typography>
              </Paper>
            </>
          )}

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
      </Box>
      {mapOpen && (
        <Box
          sx={{
            flex: 8,
            height: "100%",
          }}
        >
          <Paper
            sx={{
              p: 2,
              height: "100%",
              width: "100%",
              display: "flex",
              flexDirection: "column",
            }}
          >
            <TextField
              fullWidth
              label="Start position"
              value={weatherStart!.city.name}
              slotProps={{
                inputLabel: {
                  shrink: true,
                  sx: {
                    fontSize: "1.25rem",
                    fontWeight: 700,
                    color: "#333",
                  },
                },
                input: {
                  readOnly: true,
                },
              }}
            />
            <ForecastList weather={weatherStart!} />
          </Paper>
          <Paper
            sx={{
              p: 2,
              height: "100%",
              width: "100%",
              display: "flex",
              flexDirection: "column",
            }}
          >
            <TextField
              fullWidth
              label="End position"
              value={weatherStart!.city.name}
              slotProps={{
                inputLabel: {
                  shrink: true,
                  sx: {
                    fontSize: "1.25rem",
                    fontWeight: 700,
                    color: "#333",
                  },
                },
                input: {
                  readOnly: true,
                },
              }}
            />
            <ForecastList weather={weatherEnd!} />
          </Paper>
        </Box>
      )}
    </Dialog>
  );
};

export default ScheduleDetail;
