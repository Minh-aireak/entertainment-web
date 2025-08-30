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
import ScheduleCard from "../components/Schedule/ScheduleCard";
import ScheduleDetail from "../components/Schedule/ScheduleDetail";
import ScheduleDelete from "../components/Schedule/ScheduleDelete";
import ScheduleEdit from "../components/Schedule/ScheduleEdit";
import {
  deleteSchedule,
  getMySchedules,
  createSchedule,
  updateSchedule,
} from "../services/scheduleService";
import { CustomAlertSnackbar } from "../components/CustomAlertSnackbar";
import type {
  ScheduleResponse,
  PostDataUpdate,
} from "../InterfaceDataType/DataType";

function Schedules() {
  const [schedules, setSchedules] = useState<ScheduleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [severity, setSeverity] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [selectedSchedule, setSelectedSchedule] =
    useState<ScheduleResponse | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [formData, setFormData] = useState<PostDataUpdate>({
    title: "",
    startTime: new Date(),
    endTime: new Date(),
    content: "",
  });
  const [hasMore, setHasMore] = useState(true);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);

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
    if (loading) return;

    const nextPage = currentPage + 1;
    const nextData = await loadSchedulePerPage(nextPage, 6);
    if (nextData && nextData.length > 0) {
      setCurrentPage(nextPage);
    }
  };

  const filteredSchedules = schedules.filter((schedule) => {
    const matchesSearch =
      schedule.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      schedule.content.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesStatus =
      statusFilter === "all" ||
      (statusFilter === "Up coming" && schedule.status === "Up coming") ||
      (statusFilter === "On going" && schedule.status === "On going") ||
      (statusFilter === "Completed" && schedule.status === "Completed");

    return matchesSearch && matchesStatus;
  });

  useEffect(() => {
    const loadInitData = async () => {
      const initSchedule = await loadSchedulePerPage(1, 6);
      if (initSchedule) {
        setSchedules(initSchedule);
      }
    };
    loadInitData();
  }, []);

  const handleViewDetail = async (scheduleId: string) => {
    try {
      setLoading(true);
      const schedule = filteredSchedules.find((s) => s.id === scheduleId);
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

  const handleViewEdit = async (scheduleId: string) => {
    try {
      setLoading(true);
      const schedule = filteredSchedules.find((s) => s.id === scheduleId);
      if (schedule) {
        setSelectedSchedule(schedule);
        setEditOpen(true);
      }
    } catch (error: any) {
      setSnackbarMessage(error.message);
      setSeverity(false);
    } finally {
      setLoading(false);
    }
  };

  const handleViewDelete = async (scheduleId: string) => {
    try {
      setLoading(true);

      const schedule = filteredSchedules.find((s) => s.id === scheduleId);
      if (schedule) {
        setSelectedSchedule(schedule);
        setDeleteOpen(true);
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
        ...formData,
      };
      const response = await createSchedule(scheduleData);
      setSnackbarMessage(response.data.message);
      setSeverity(true);
      setCreateDialogOpen(false);
      setFormData({
        title: "",
        startTime: new Date(),
        endTime: new Date(),
        content: "",
      });
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

  const handleDeleteSchedule = async (scheduleId: string) => {
    try {
      setLoading(true);
      const response = await deleteSchedule(scheduleId);
      setSnackbarMessage(response.data.message);
      setSeverity(true);
      setDeleteOpen(false);
      setSchedules((prev) =>
        prev.filter((schedule) => schedule.id !== scheduleId)
      );
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
    setFormData({
      title: "",
      startTime: new Date(),
      endTime: new Date(),
      content: "",
    });
  };

  const handleFormChange = (field: keyof PostDataUpdate, value: string) => {
    setFormData((prev) => ({
      ...prev,
      [field]: value,
    }));
  };

  const handleRefresh = async () => {
    setCurrentPage(1);
    setHasMore(true);
    setSchedules([]);
    const refreshData = await loadSchedulePerPage(1, 6);
    if (refreshData) {
      setSchedules(refreshData);
    }
  };

  const handleEditSchedule = async (
    scheduleId: string,
    scheduleData: PostDataUpdate
  ) => {
    try {
      setLoading(true);
      const response = await updateSchedule(scheduleId, scheduleData);
      setSnackbarMessage(response.data.message);
      setSeverity(true);
      setEditOpen(false);
      setSelectedSchedule(response.data.result);
      setSchedules((prev) =>
        prev.map((s) =>
          s.id === response.data.result.id ? response.data.result : s
        )
      );
      handleRefresh;
    } catch (error: any) {
      setSnackbarMessage(error.message);
      setSeverity(false);
    } finally {
      setSnackbarOpen(true);
      setLoading(false);
    }
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
              value={formData.title}
              onChange={(e) => handleFormChange("title", e.target.value)}
              required
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
            <TextField
              fullWidth
              label="Start Time"
              type="datetime-local"
              value={formData.startTime}
              onChange={(e) => handleFormChange("startTime", e.target.value)}
              required
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
            <TextField
              fullWidth
              label="End Time"
              type="datetime-local"
              value={formData.endTime}
              onChange={(e) => handleFormChange("endTime", e.target.value)}
              required
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
            <TextField
              fullWidth
              label="Content"
              multiline
              minRows={10}
              value={formData.content}
              onChange={(e) => handleFormChange("content", e.target.value)}
              required
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
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseCreateDialog}>Cancel</Button>
          <Button
            onClick={handleCreateSchedule}
            variant="contained"
            disabled={
              loading ||
              !formData.title ||
              !formData.startTime ||
              !formData.endTime ||
              !formData.content
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
                <MenuItem value="Up coming">Up coming</MenuItem>
                <MenuItem value="On going">On going</MenuItem>
                <MenuItem value="Completed">Completed</MenuItem>
              </Select>
            </FormControl>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<Refresh />}
              onClick={handleRefresh}
              disabled={loading}
            >
              Refresh
            </Button>
          </Box>
        </Paper>

        {loading && schedules.length === 0 ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 8 }} />
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
              dataLength={filteredSchedules.length}
              next={loadMore}
              hasMore={hasMore}
              loader={null}
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
                      onDelete={handleViewDelete}
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
          onCloseDetail={() => setDetailOpen(false)}
          onEdit={handleViewEdit}
          onDelete={handleViewDelete}
        />

        <ScheduleDelete
          open={deleteOpen}
          scheduleId={selectedSchedule?.id ?? ""}
          onClose={() => setDeleteOpen(false)}
          onDelete={handleDeleteSchedule}
        />

        <ScheduleEdit
          open={editOpen}
          schedule={selectedSchedule}
          onClose={() => setEditOpen(false)}
          onSave={handleEditSchedule}
        />
      </Container>
    </>
  );
}

export default Schedules;
