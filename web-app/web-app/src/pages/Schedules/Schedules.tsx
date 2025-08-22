import { useState, useEffect } from "react";
import {
  Container,
  Typography,
  Box,
  Button,
  TextField,
  InputAdornment,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Pagination,
  CircularProgress,
  Alert,
  Fab,
  Paper,
} from "@mui/material";
import {
  Search,
  Add,
  Refresh,
  Schedule as ScheduleIcon,
} from "@mui/icons-material";
import ScheduleCard from "../../components/Schedule/ScheduleCard";
import ScheduleDetail from "../../components/Schedule/ScheduleDetail";
import { deleteSchedule, getMySchedules, createSchedule } from "../../services/scheduleService";
import { CustomAlertSnackbar } from "../../components/CustomAlertSnackbar";
import type { Schedule } from "../../InterfaceDataType/DataTypeResponse";

type DataCreatePostRequest = {
        title: string;
  startTime: string;
  endTime: string;
  content?: string;
}

function Schedules() {
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [loading, setLoading] = useState(true);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [severity, setSeverity] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(
    null
  );
  const [detailOpen, setDetailOpen] = useState(false);
  const [totalPages, setTotalPages] = useState(1);
  const [createNewSchedule, setCreateNewSchedule] = useState<DataCreatePostRequest | null>(null);

  const loadSchedules = async (page: number) => {
    try {
      setLoading(true);
      await getMySchedules(page).then((response) => {
        setTotalPages(response.data.result?.totalPages);
        setSchedules((prev) => [...prev, ...response.data.result?.data]);
      }).catch;
      (error: any) => {
        setSnackbarMessage(error.message);
        setSnackbarOpen(true);
        setSeverity(false);
      };
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSchedules(currentPage);
  }, [currentPage]);

  const filteredSchedules = schedules.filter((schedule) => {
    const matchesSearch =
      schedule.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      schedule.content.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesStatus =
      statusFilter === "all" || schedule.status === statusFilter;

    return matchesSearch && matchesStatus;
  });

  const handleViewDetail = async (scheduleId: string) => {
    try {
      setLoading(true);

      const schedule = schedules.find((s) => s.id === scheduleId);
      if (schedule) {
        setSelectedSchedule(schedule);
        setDetailOpen(true);  
        setSelectedSchedule(schedule);
      }
    } catch (error: any) {
      setSnackbarMessage(error.message);
      setSeverity(false); 
    } finally{
      setSnackbarOpen(true);
      setLoading(false);
    }
  };

  const handleEditSchedule = (scheduleId: string) => {
    // TODO: Implement edit functionality
    console.log("Edit schedule:", scheduleId);
  };

  const handleDeleteSchedule = async (scheduleId: string) => {
    try {
      setLoading(true);
      await deleteSchedule(scheduleId).then((response) => {
        setSnackbarMessage(response.data.message);
      }).catch;
      (error: any) => {
        setSnackbarMessage(error.message);
        setSeverity(true);
      };
    } finally {
      setSnackbarOpen(true);
      setLoading(false);
    }
    };

      const handleCreateSchedule = async (createNewSchedule: DataCreatePostRequest) => {
    try {
      setLoading(true);
      const response = await createSchedule(createNewSchedule);
        setSnackbarMessage(response.data.message);
      }catch(error: any) {
        setSnackbarMessage(error.message);
        setSeverity(true);
      }finally {
      setSnackbarOpen(true);
      setLoading(false);
    }
  };
  };

  return (
    <>
      <CustomAlertSnackbar
        open={snackbarOpen}
        message={snackbarMessage}
        severity={severity ? "success" : "error"}
        onClose={() => setSnackbarOpen(false)}
      />
      <Container maxWidth="xl" sx={{ py: 4 }}>
        <Box sx={{ mb: 4 }}>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              mb: 3,
            }}
          >
            <Box sx={{ display: "flex", alignItems: "center" }}>
              <ScheduleIcon
                sx={{ fontSize: 32, color: "primary.main", mr: 2 }}
              />
              <Typography variant="h4" component="h1" sx={{ fontWeight: 700 }}>
                My schedules
              </Typography>
            </Box>
            <Button
              variant="contained"
              startIcon={<Add />}
              onClick={handleCreateSchedule}
              sx={{ borderRadius: 2 }}
            >
              Create new schedule
            </Button>
          </Box>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: {
                xs: "1fr",
                sm: "repeat(2, 1fr)",
                md: "repeat(4, 1fr)",
              },
              gap: 2,
              mb: 3,
            }}
          >
            <Paper sx={{ p: 2, textAlign: "center" }}>
              <Typography
                variant="h4"
                color="primary.main"
                sx={{ fontWeight: 700 }}
              >
                {schedules.filter((s) => s.status === "upcoming").length}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Up coming
              </Typography>
            </Paper>
            <Paper sx={{ p: 2, textAlign: "center" }}>
              <Typography
                variant="h4"
                color="success.main"
                sx={{ fontWeight: 700 }}
              >
                {schedules.filter((s) => s.status === "ongoing").length}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                On going
              </Typography>
            </Paper>
            <Paper sx={{ p: 2, textAlign: "center" }}>
              <Typography
                variant="h4"
                color="text.secondary"
                sx={{ fontWeight: 700 }}
              >
                {schedules.filter((s) => s.status === "completed").length}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Completed
              </Typography>
            </Paper>
            <Paper sx={{ p: 2, textAlign: "center" }}>
              <Typography
                variant="h4"
                color="error.main"
                sx={{ fontWeight: 700 }}
              >
                {schedules.filter((s) => s.status === "cancelled").length}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Cancelled
              </Typography>
            </Paper>
          </Box>
        </Box>

        <Paper sx={{ p: 3, mb: 3 }}>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: { xs: "1fr", md: "2fr 1fr 1fr" },
              gap: 2,
              alignItems: "center",
            }}
          >
            <TextField
              fullWidth
              placeholder="Search the schedule..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              slotProps={{
    textField: {
      InputProps: {
        startAdornment: (
          <InputAdornment position="start">
            <SearchIcon />
          </InputAdornment>
        ),
      },
    },
  }}
            />
            <FormControl fullWidth>
              <InputLabel>Status</InputLabel>
              <Select
                value={statusFilter}
                label="Status"
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <MenuItem value="all">All</MenuItem>
                <MenuItem value="upcoming">Up coming</MenuItem>
                <MenuItem value="ongoing">On going</MenuItem>
                <MenuItem value="completed">Completed</MenuItem>
                <MenuItem value="cancelled">Cancelled</MenuItem>
              </Select>
            </FormControl>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<Refresh />}
              onClick={loadSchedules}
              disabled={loading}
            >
              Refresh
            </Button>
          </Box>
        </Paper>

        {/* Content */}
        {error && (
          <Alert severity="error" sx={{ mb: 3 }}>
            {error}
          </Alert>
        )}

        {loading ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
            <CircularProgress />
          </Box>
        ) : filteredSchedules.length === 0 ? (
          <Paper sx={{ p: 8, textAlign: "center" }}>
            <ScheduleIcon
              sx={{ fontSize: 64, color: "text.secondary", mb: 2 }}
            />
            <Typography variant="h6" color="text.secondary" sx={{ mb: 1 }}>
              Không tìm thấy lịch trình nào
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {searchTerm || statusFilter !== "all"
                ? "Thử thay đổi bộ lọc tìm kiếm"
                : "Bạn chưa có lịch trình nào. Hãy tạo lịch trình đầu tiên!"}
            </Typography>
          </Paper>
        ) : (
          <>
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: {
                  xs: "1fr",
                  sm: "repeat(2, 1fr)",
                  md: "repeat(3, 1fr)",
                },
                gap: 3,
              }}
            >
              {filteredSchedules.map((schedule) => (
                <Box key={schedule.id}>
                  <ScheduleCard
                    schedule={schedule}
                    onViewDetail={handleViewDetail}
                    onEdit={handleEditSchedule}
                    onDelete={handleDeleteSchedule}
                  />
                </Box>
              ))}
            </Box>

            {/* Pagination */}
            {totalPages > 1 && (
              <Box sx={{ display: "flex", justifyContent: "center", mt: 4 }}>
                <Pagination
                  count={totalPages}
                  page={currentPage}
                  onChange={(_, page) => setCurrentPage(page)}
                  color="primary"
                  size="large"
                />
              </Box>
            )}
          </>
        )}

        {/* Schedule Detail Dialog */}
        <ScheduleDetail
          schedule={selectedSchedule}
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          onEdit={handleEditSchedule}
          onDelete={handleDeleteSchedule}
        />

        {/* Floating Action Button */}
        <Fab
          color="primary"
          aria-label="add"
          onClick={handleCreateSchedule}
          sx={{
            position: "fixed",
            bottom: 16,
            right: 16,
          }}
        >
          <Add />
        </Fab>
      </Container>
    </>
  );
}

export default Schedules;
