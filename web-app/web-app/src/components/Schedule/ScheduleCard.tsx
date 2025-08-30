import React from "react";
import {
  Card,
  CardContent,
  Typography,
  Chip,
  Box,
  IconButton,
  CardActions,
} from "@mui/material";
import { AccessTime, Visibility, Delete } from "@mui/icons-material";
import type { ScheduleResponse } from "../../InterfaceDataType/DataType";
import dayjs from "dayjs";

interface ScheduleCardProps {
  schedule: ScheduleResponse;
  onViewDetail: (scheduleId: string) => void;
  onEdit?: (scheduleId: string) => void;
  onDelete?: (scheduleId: string) => void;
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
  }
};

const ScheduleCard: React.FC<ScheduleCardProps> = React.memo(
  ({ schedule, onViewDetail, onDelete }) => {
    return (
      <Card
        sx={{
          maxWidth: 460,
          height: "100%",
          display: "flex",
          flexDirection: "column",
          transition: "transform 0.2s ease-in-out, box-shadow 0.2s ease-in-out",
          "&:hover": {
            transform: "translateY(-4px)",
            boxShadow: "0 8px 25px rgba(0,0,0,0.15)",
            cursor: "pointer",
          },
          borderRight: "1px solid",
          borderBottom: "1px solid",
          borderColor: "grey.400",
        }}
        onClick={() => onViewDetail(schedule.id)}
      >
        <CardContent
          sx={{ flexGrow: 1, display: "flex", flexDirection: "column" }}
        >
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "flex-start",
              mb: 1,
            }}
          >
            <Typography
              variant="h6"
              component="div"
              sx={{
                fontWeight: 600,
                fontSize: 20,
                overflow: "hidden",
                textOverflow: "ellipsis",
                display: "-webkit-box",
                WebkitLineClamp: 2,
                WebkitBoxOrient: "vertical",
              }}
            >
              {schedule.title}
            </Typography>
            <Chip
              label={getStatusText(schedule.status)}
              color={getStatusColor(schedule.status) as any}
              size="small"
              sx={{ ml: 1, flexShrink: 0 }}
            />
          </Box>

          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              mb: 2,
              fontWeight: "bold",
              fontSize: 15,
              overflow: "hidden",
              textOverflow: "ellipsis",
              display: "-webkit-box",
              WebkitLineClamp: 3,
              WebkitBoxOrient: "vertical",
            }}
          >
            {schedule.content}
          </Typography>

          <Box sx={{ mt: "auto" }}>
            <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
              <AccessTime
                sx={{ fontSize: 20, color: "text.secondary", mr: 0.5 }}
              />
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ fontWeight: 600, fontSize: 13 }}
              >
                {dayjs(schedule.startTime).format("DD/MM/YYYY HH:mm")} -{" "}
                {dayjs(schedule.endTime).format("DD/MM/YYYY HH:mm")}
              </Typography>
            </Box>

            <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ fontWeight: 600, fontSize: 13 }}
              >
                Created: {schedule.createdDate}
              </Typography>
            </Box>
          </Box>
        </CardContent>

        <CardActions sx={{ justifyContent: "space-between", p: 2, pt: 0 }}>
          <Box>
            {onDelete && (
              <IconButton
                size="small"
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete(schedule.id);
                }}
                sx={{ color: "error.main" }}
              >
                <Delete />
              </IconButton>
            )}
          </Box>

          <IconButton
            size="small"
            onClick={(e) => {
              e.stopPropagation();
              onViewDetail(schedule.id);
            }}
            sx={{ color: "primary.main" }}
          >
            <Visibility />
          </IconButton>
        </CardActions>
      </Card>
    );
  }
);
export default ScheduleCard;
