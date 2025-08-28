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
  CircularProgress,
  Fab,
  Paper,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from "@mui/material";
import {
  Search,
  Add,
  Refresh,
  Schedule as ScheduleIcon,
} from "@mui/icons-material";
import InfiniteScroll from "react-infinite-scroll-component";
import ScheduleCard from "../../components/Schedule/ScheduleCard";
import ScheduleDetail from "../../components/Schedule/ScheduleDetail";
import {
  deleteSchedule,
  getMySchedules,
  createSchedule,
} from "../../services/scheduleService";
import { CustomAlertSnackbar } from "../../components/CustomAlertSnackbar";
import type { Schedule } from "../../InterfaceDataType/DataTypeResponse";

type DataCreatePostRequest = {
  title: string;
  startTime: string;
  endTime: string;
  content: string;
};

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
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [createForm, setCreateForm] = useState<DataCreatePostRequest>({
    title: "",
    startTime: "",
    endTime: "",
    content: "",
  });
  const [hasMore, setHasMore] = useState(true);
  const [totalSize, setTotalSize] = useState(0);

  const loadSchedulePerPage = async (page: number, size: number) => {
    setLoading(true);
    try {
      const response = await getMySchedules(page, size);
      setHasMore(response.data.result.data.length > 0);
      setSchedules((prev) => [...prev, ...response.data.result.data]);
      return response.data.result.data;
    } catch (error: any) {
      setSnackbarMessage(error.message);
      setSnackbarOpen(true);
      setSeverity(false);
    } finally {
      setLoading(false);
    }
  };

  const loadMore = async () => {
    const nextPage = currentPage + 1;
    const nextData = await loadSchedulePerPage(nextPage, 6);
    if (nextData) {
      setCurrentPage(nextPage);
    }
  };

  useEffect(() => {
    if (!hasMore) return;
    const loadInitData = async () => {
      const initSchedule = await loadSchedulePerPage(1, 6);
      if (initSchedule) {
        setSchedules(initSchedule);
      }
    };
    loadInitData();
  }, []);

  const filteredSchedules = schedules.filter((schedule) => {
    const matchesSearch =
      schedule.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      schedule.content.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesStatus =
      statusFilter === "all" ||
      (statusFilter === "upcoming" && schedule.status === "Up coming") ||
      (statusFilter === "ongoing" && schedule.status === "On going") ||
      (statusFilter === "completed" && schedule.status === "Completed") ||
      (statusFilter === "cancelled" && schedule.status === "Cancelled");

    return matchesSearch && matchesStatus;
  });

  const handleViewDetail = async (scheduleId: string) => {
    try {
      setLoading(true);

      const schedule = schedules.find((s) => s.id === scheduleId);
      if (schedule) {
        setSelectedSchedule(schedule);
        setDetailOpen(true);
      }
    } catch (error: any) {
      setSnackbarMessage(error.message);
      setSeverity(false);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateSchedule = async () => {
    try {
      setLoading(true);
      const scheduleData = {
        ...createForm,
      };
      const response = await createSchedule(scheduleData);
      setSnackbarMessage(response.data.message);
      setSeverity(true);
      setCreateDialogOpen(false);
      setCreateForm({ title: "", startTime: "", endTime: "", content: "" });
      setCurrentPage(1);
      setHasMore(true);
      setSchedules([]);
      const initSchedule = await loadSchedulePerPage(1, 6);
      if (initSchedule) {
        setSchedules(initSchedule);
      }
    } catch (error: any) {
      setSnackbarMessage(error.message);
      setSeverity(false);
    } finally {
      setSnackbarOpen(true);
      setLoading(false);
    }
  };
  /////////////////
  const handleEditSchedule = (scheduleId: string) => {
    console.log("Edit schedule:", scheduleId);
  };

  const handleDeleteSchedule = async (scheduleId: string) => {
    try {
      setLoading(true);
      const response = await deleteSchedule(scheduleId);
      setSnackbarMessage(response.data.message);
      setSeverity(true);
      setSchedules((prev) =>
        prev.filter((schedule) => schedule.id !== scheduleId)
      );
      if (selectedSchedule?.id === scheduleId) {
        setDetailOpen(false);
        setSelectedSchedule(null);
      }
    } catch (error: any) {
      setSnackbarMessage(error.message);
      setSeverity(false);
    } finally {
      setSnackbarOpen(true);
      setLoading(false);
    }
  };

  const handleCreateButtonClick = () => {
    setCreateDialogOpen(true);
  };

  const handleCloseCreateDialog = () => {
    setCreateDialogOpen(false);
    setCreateForm({ title: "", startTime: "", endTime: "", content: "" });
  };

  const handleFormChange = (
    field: keyof DataCreatePostRequest,
    value: string
  ) => {
    setCreateForm((prev) => ({
      ...prev,
      [field]: value,
    }));
  };

  return (
    <>
      <CustomAlertSnackbar
        open={snackbarOpen}
        message={snackbarMessage}
        severity={severity ? "success" : "error"}
        onClose={() => setSnackbarOpen(false)}
      />

      <Dialog
        open={createDialogOpen}
        onClose={handleCloseCreateDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Create New Schedule</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2, display: "flex", flexDirection: "column", gap: 2 }}>
            <TextField
              fullWidth
              label="Title"
              value={createForm.title}
              onChange={(e) => handleFormChange("title", e.target.value)}
              required
            />
            <TextField
              fullWidth
              label="Start Time"
              type="datetime-local"
              value={createForm.startTime}
              onChange={(e) => handleFormChange("startTime", e.target.value)}
              required
              slotProps={{
                inputLabel: {
                  shrink: true,
                },
              }}
            />
            <TextField
              fullWidth
              label="End Time"
              type="datetime-local"
              value={createForm.endTime}
              onChange={(e) => handleFormChange("endTime", e.target.value)}
              required
              slotProps={{
                inputLabel: {
                  shrink: true,
                },
              }}
            />
            <TextField
              fullWidth
              label="Content"
              multiline
              rows={4}
              value={createForm.content}
              onChange={(e) => handleFormChange("content", e.target.value)}
              required
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseCreateDialog}>Cancel</Button>
          <Button
            onClick={handleCreateSchedule}
            variant="contained"
            disabled={
              loading ||
              !createForm.title ||
              !createForm.startTime ||
              !createForm.endTime ||
              !createForm.content
            }
          >
            {loading ? <CircularProgress size={20} /> : "Create"}
          </Button>
        </DialogActions>
      </Dialog>

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
            <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
              <Button
                variant="contained"
                startIcon={<Add />}
                onClick={handleCreateButtonClick}
                sx={{ borderRadius: 2 }}
              >
                Create new schedule
              </Button>
            </Box>
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
                {schedules.filter((s) => s.status === "Up coming").length}
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
                {schedules.filter((s) => s.status === "On going").length}
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
                {schedules.filter((s) => s.status === "Completed").length}
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
                {schedules.filter((s) => s.status === "Cancelled").length}
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
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search />
                    </InputAdornment>
                  ),
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
              onClick={async () => {
                setCurrentPage(1);
                setHasMore(true);
                setSchedules([]);
                const refreshData = await loadSchedulePerPage(1, 6);
                if (refreshData) {
                  setSchedules(refreshData);
                }
              }}
              disabled={loading}
            >
              Refresh
            </Button>
          </Box>
        </Paper>

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
              No schedule found
            </Typography>
          </Paper>
        ) : (
          <>
            <InfiniteScroll
              dataLength={schedules.length}
              next={loadMore}
              hasMore={hasMore}
              loader={
                <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
                  <CircularProgress />
                </Box>
              }
            >
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
            </InfiniteScroll>
          </>
        )}

        <ScheduleDetail
          schedule={selectedSchedule}
          open={detailOpen}
          onClose={() => setDetailOpen(false)}
          onEdit={handleEditSchedule}
          onDelete={handleDeleteSchedule}
        />
        <Fab
          color="primary"
          aria-label="add"
          onClick={handleCreateButtonClick}
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