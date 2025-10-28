import React from "react";
import {
  Card,
  CardContent,
  Typography,
  Chip,
  Box,
  IconButton,
  Avatar,
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
        elevation={8}
        sx={{
          height: "100%",
          display: "flex",
          flexDirection: "column",
          borderRadius: 3,
          overflow: "hidden",
          background: "white",
          border: "1px solid rgba(102, 126, 234, 0.1)",
          position: "relative",
          transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
          "&:hover": {
            transform: "translateY(-8px) scale(1.02)",
            boxShadow: "0 20px 40px rgba(102, 126, 234, 0.2)",
            cursor: "pointer",
            "& .card-header": {
              background: "linear-gradient(45deg, #667eea 30%, #764ba2 90%)",
              "& .MuiTypography-root": {
                color: "white",
              },
              "& .MuiAvatar-root": {
                transform: "scale(1.1)",
                boxShadow: "0 4px 12px rgba(0,0,0,0.3)",
              },
            },
            "& .status-chip": {
              transform: "scale(1.05)",
            },
            "& .action-buttons": {
              opacity: 1,
              transform: "translateY(0)",
            },
          },
          "&::before": {
            content: '""',
            position: "absolute",
            top: 0,
            left: 0,
            right: 0,
            height: "4px",
            background: "linear-gradient(45deg, #667eea 30%, #764ba2 90%)",
            zIndex: 1,
          },
        }}
        onClick={() => onViewDetail(schedule.id)}
      >
        <CardContent
          sx={{ flexGrow: 1, display: "flex", flexDirection: "column", p: 0 }}
        >
          {/* Header Section */}
          <Box
            className="card-header"
            sx={{
              background: "linear-gradient(45deg, #f8f9ff 0%, #ffffff 100%)",
              p: 3,
              transition: "all 0.3s ease",
            }}
          >
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
                sx={{
                  width: 40,
                  height: 40,
                  mr: 2,
                  border: "2px solid white",
                  boxShadow: "0 2px 8px rgba(0,0,0,0.1)",
                  transition: "all 0.3s ease",
                }}
              />
              <Box sx={{ flex: 1 }}>
                <Typography
                  variant="body2"
                  sx={{
                    fontWeight: 600,
                    color: "text.primary",
                    transition: "color 0.3s ease",
                  }}
                >
                  {schedule.displayName}
                </Typography>
                <Typography
                  variant="caption"
                  sx={{
                    color: "text.secondary",
                    transition: "color 0.3s ease",
                  }}
                >
                  Created: {schedule.createdDate}
                </Typography>
              </Box>
              <Chip
                className="status-chip"
                label={getStatusText(schedule.status)}
                color={getStatusColor(schedule.status) as any}
                size="small"
                sx={{
                  fontWeight: 600,
                  transition: "all 0.3s ease",
                  boxShadow: "0 2px 4px rgba(0,0,0,0.1)",
                }}
              />
            </Box>

            <Typography
              variant="h6"
              component="div"
              sx={{
                fontWeight: 700,
                fontSize: "1.25rem",
                overflow: "hidden",
                textOverflow: "ellipsis",
                display: "-webkit-box",
                WebkitLineClamp: 2,
                WebkitBoxOrient: "vertical",
                color: "text.primary",
                transition: "color 0.3s ease",
                lineHeight: 1.3,
              }}
            >
              {schedule.title}
            </Typography>
          </Box>

          {/* Content Section */}
          <Box sx={{ p: 3, flex: 1 }}>
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                mb: 3,
                fontWeight: 500,
                fontSize: "0.95rem",
                overflow: "hidden",
                textOverflow: "ellipsis",
                display: "-webkit-box",
                WebkitLineClamp: 3,
                WebkitBoxOrient: "vertical",
                lineHeight: 1.5,
              }}
            >
              {schedule.content}
            </Typography>

            <Box sx={{ mt: "auto" }}>
              <Box
                sx={{
                  display: "flex",
                  alignItems: "center",
                  mb: 2,
                  p: 2,
                  borderRadius: 2,
                  background: "rgba(102, 126, 234, 0.05)",
                  border: "1px solid rgba(102, 126, 234, 0.1)",
                }}
              >
                <AccessTime
                  sx={{ fontSize: 20, color: "primary.main", mr: 1 }}
                />
                <Box>
                  <Typography
                    variant="body2"
                    color="text.primary"
                    sx={{ fontWeight: 600, fontSize: "0.85rem" }}
                  >
                    {dayjs(schedule.startTime).format("DD/MM/YYYY HH:mm")}
                  </Typography>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ fontSize: "0.75rem" }}
                  >
                    to {dayjs(schedule.endTime).format("DD/MM/YYYY HH:mm")}
                  </Typography>
                </Box>
              </Box>
            </Box>
          </Box>

          {/* Action Buttons */}
          <Box
            className="action-buttons"
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              p: 2,
              background: "rgba(248, 249, 255, 0.8)",
              borderTop: "1px solid rgba(102, 126, 234, 0.1)",
              opacity: 0.7,
              transform: "translateY(10px)",
              transition: "all 0.3s ease",
            }}
          >
            <Box>
              {onDelete && (
                <IconButton
                  size="small"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(schedule.id);
                  }}
                  sx={{
                    color: "error.main",
                    background: "rgba(244, 67, 54, 0.1)",
                    "&:hover": {
                      background: "rgba(244, 67, 54, 0.2)",
                      transform: "scale(1.1)",
                    },
                    transition: "all 0.2s ease",
                  }}
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
              sx={{
                color: "primary.main",
                background: "rgba(102, 126, 234, 0.1)",
                "&:hover": {
                  background: "rgba(102, 126, 234, 0.2)",
                  transform: "scale(1.1)",
                },
                transition: "all 0.2s ease",
              }}
            >
              <Visibility />
            </IconButton>
          </Box>
        </CardContent>
      </Card>
    );
  }
);
export default ScheduleCard;
