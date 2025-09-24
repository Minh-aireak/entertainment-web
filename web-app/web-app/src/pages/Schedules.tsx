import { useState, useEffect, useRef } from "react";
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
  Paper,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from "@mui/material";
import * as L from "leaflet";
import "leaflet/dist/leaflet.css";
import "@geoapify/leaflet-address-search-plugin";
import "@geoapify/leaflet-address-search-plugin/dist/L.Control.GeoapifyAddressSearch.min.css";
import {
  type LatLon,
  type DataWeatherResponse,
} from "../InterfaceDataType/DataType";
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
} from "../services/ScheduleService";
import { getDataWeather } from "../services/WeatherService";
import { CustomAlertSnackbar } from "../components/CustomAlertSnackbar";
import type {
  ScheduleResponse,
  PostData,
  UpdatePostData,
} from "../InterfaceDataType/DataType";
import ForecastList from "../components/ForecastCard/ForecastList";
import axios from "axios";

function Schedules() {
  const [schedules, setSchedules] = useState<ScheduleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [severity, setSeverity] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [searchTerm, setSearchTerm] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [typeFilter, setTypeFilter] = useState<string>("BUSINESS_SCHEDULE");
  const [selectedSchedule, setSelectedSchedule] =
    useState<ScheduleResponse | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [formData, setFormData] = useState<PostData>({
    postType: "",
    title: "",
    startTime: new Date(),
    endTime: new Date(),
    content: "",
    latStart: "",
    lonStart: "",
    latEnd: "",
    lonEnd: "",
  });
  const [hasMore, setHasMore] = useState(true);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [mapOpen, setMapOpen] = useState(false);
  const mapRef = useRef<L.Map | null>(null);
  const [positionStart, setPositionStart] = useState<LatLon | null>(null);
  const markerStartRef = useRef<L.Marker | null>(null);
  const [positionEnd, setPositionEnd] = useState<LatLon | null>(null);
  const markerEndRef = useRef<L.Marker | null>(null);
  const [weatherStart, setWeatherStart] = useState<DataWeatherResponse | null>(
    null
  );
  const [weatherEnd, setWeatherEnd] = useState<DataWeatherResponse | null>(
    null
  );

  const loadSchedulePerPage = async (
    page: number,
    size: number,
    type: string
  ) => {
    setLoading(true);
    try {
      const response = await getMySchedules(page, size, type);
      setHasMore(response.data.result.data.length > 0);
      setSchedules((prev) => [...prev, ...response.data.result.data]);
      return response.data.result.data;
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSnackbarOpen(true);
      setSeverity(false);
    } finally {
      setLoading(false);
    }
  };

  const loadMore = async () => {
    if (loading) return;

    const nextPage = currentPage + 1;
    const nextData = await loadSchedulePerPage(nextPage, 6, typeFilter);
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
      const initSchedule = await loadSchedulePerPage(1, 6, typeFilter);
      if (initSchedule) {
        setSchedules(initSchedule);
      }
    };
    loadInitData();
  }, [typeFilter]);

  const handleViewDetail = async (scheduleId: string) => {
    try {
      setLoading(true);
      const schedule = filteredSchedules.find((s) => s.id === scheduleId);
      if (schedule) {
        setSelectedSchedule(schedule);
        setDetailOpen(true);
      }
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
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
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
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
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSeverity(false);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateSchedule = async () => {
    try {
      setLoading(true);
      setMapOpen(false);
      const scheduleData = {
        ...formData,
      };

      const response = await createSchedule(scheduleData);
      setSnackbarMessage(response.data.message);
      setSeverity(true);
      setCreateDialogOpen(false);
      setFormData({
        postType: "",
        title: "",
        startTime: new Date(),
        endTime: new Date(),
        content: "",
        latStart: "",
        lonStart: "",
        latEnd: "",
        lonEnd: "",
      });
      setCurrentPage(1);
      setTypeFilter(scheduleData.postType);
      setHasMore(true);
      setSchedules([]);
      const initSchedule = await loadSchedulePerPage(1, 6, typeFilter);
      if (initSchedule) {
        setSchedules(initSchedule);
      }
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSeverity(false);
    } finally {
      setSnackbarOpen(true);
      setLoading(false);
    }
  };

  const handleDeleteSchedule = async (scheduleId: string, type: string) => {
    try {
      setLoading(true);
      const response = await deleteSchedule(scheduleId, type);
      setSnackbarMessage(response.data.message);
      setSeverity(true);
      setDeleteOpen(false);
      setSchedules((prev) =>
        prev.filter((schedule) => schedule.id !== scheduleId)
      );
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSeverity(false);
    } finally {
      setSnackbarOpen(true);
      setLoading(false);
      setDetailOpen(false);
    }
  };

  const handleCreateButtonClick = () => {
    setMapOpen(false);
    setCreateDialogOpen(true);
  };

  const handleCloseCreateDialog = () => {
    setCreateDialogOpen(false);
    setFormData({
      postType: "",
      title: "",
      startTime: new Date(),
      endTime: new Date(),
      content: "",
      latStart: "",
      lonStart: "",
      latEnd: "",
      lonEnd: "",
    });
    setPositionStart(null);
    setPositionEnd(null);
    setWeatherStart(null);
    setWeatherEnd(null);
  };

  const handleFormChange = (field: keyof PostData, value: string) => {
    setFormData((prev) => ({
      ...prev,
      [field]: value,
    }));
  };

  const handleRefresh = async () => {
    setCurrentPage(1);
    setHasMore(true);
    setSchedules([]);
    const refreshData = await loadSchedulePerPage(1, 6, typeFilter);
    if (refreshData) {
      setSchedules(refreshData);
    }
  };

  const handleEditSchedule = async (
    scheduleId: string,
    type: string,
    scheduleData: UpdatePostData
  ) => {
    try {
      setLoading(true);
      const response = await updateSchedule(scheduleId, type, scheduleData);
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
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSeverity(false);
    } finally {
      setSnackbarOpen(true);
      setLoading(false);
    }
  };

  useEffect(() => {
    if (typeof window === "undefined") return;
    if (mapRef.current) return;
    if (!mapOpen) return;

    const myAPIKey = "c4dcc95ab99e41eea2d7e0424ed60066";
    const mapURLTemplate =
      "https://maps.geoapify.com/v1/tile/{mapStyle}/{z}/{x}/{y}{r}.png?apiKey={apiKey}";

    const map = L.map("my-map", {
      center: [21.0278, 105.8342],
      zoom: 10,
    });

    const tileUrl = mapURLTemplate
      .replace("{mapStyle}", "osm-bright-smooth")
      .replace("{apiKey}", myAPIKey);

    L.tileLayer(tileUrl, {
      maxZoom: 20,
      detectRetina: true,
    }).addTo(map);

    const addressStartSearchControl = (L.control as any).addressSearch(
      myAPIKey,
      {
        position: "topleft",
        placeholder: "Choose start location...",
        mapViewBias: true,
        resultCallback: (selected: any) => {
          if (selected && selected.lat && selected.lon) {
            setPositionStart({ lat: selected.lat, lon: selected.lon });
            if (markerStartRef.current) {
              map.removeLayer(markerStartRef.current);
            }
            const coloredIcon = L.icon({
              iconUrl:
                "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png",
              shadowUrl:
                "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png",
              iconSize: [25, 41],
              iconAnchor: [12, 41],
              popupAnchor: [1, -34],
              shadowSize: [41, 41],
            });
            markerStartRef.current = L.marker([selected.lat, selected.lon], {
              icon: coloredIcon,
            }).addTo(map);
            map.setView([selected.lat, selected.lon], 10);
          }
        },
      }
    );

    const addressEndSearchControl = (L.control as any).addressSearch(myAPIKey, {
      position: "topleft",
      placeholder: "Choose place to visit...",
      mapViewBias: true,
      resultCallback: (selected: any) => {
        if (selected && selected.lat && selected.lon) {
          setPositionEnd({ lat: selected.lat, lon: selected.lon });
          if (markerEndRef.current) {
            map.removeLayer(markerEndRef.current);
          }
          const coloredIcon = L.icon({
            iconUrl:
              "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png",
            shadowUrl:
              "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png",
            iconSize: [25, 41],
            iconAnchor: [12, 41],
            popupAnchor: [1, -34],
            shadowSize: [41, 41],
          });
          markerEndRef.current = L.marker([selected.lat, selected.lon], {
            icon: coloredIcon,
          }).addTo(map);
          map.setView([selected.lat, selected.lon], 10);
        }
      },
    });

    map.addControl(addressStartSearchControl);
    map.addControl(addressEndSearchControl);

    return () => {
      if (markerStartRef.current) {
        markerStartRef.current.remove();
        setPositionStart(null);
        markerStartRef.current = null;
      }

      if (markerEndRef.current) {
        markerEndRef.current.remove();
        setPositionEnd(null);
        markerEndRef.current = null;
      }
    };
  }, [mapOpen]);

  useEffect(() => {
    (async () => {
      if (positionStart) {
        try {
          const response = await getDataWeather(positionStart);
          const listForecast = response.list;
          const nowMs = new Date().getTime();

          const forecastNext = listForecast.filter((item: any) => {
            return new Date(item.dt).getTime() >= nowMs;
          });

          const result: DataWeatherResponse = {
            list: forecastNext,
            city: response.city,
          };
          setFormData((prev) => ({
            ...prev,
            latStart: positionStart.lat,
            lonStart: positionStart.lon,
          }));
          setWeatherStart(result);
        } catch (error) {
          console.error(
            "Error fetching weather data for start position:",
            error
          );
        }
      }
    })();
  }, [positionStart]);

  useEffect(() => {
    (async () => {
      if (positionEnd) {
        try {
          const response = await getDataWeather(positionEnd);
          const listForecast = response.list;
          const nowMs = new Date().getTime();
          console.log(response);

          const forecastNext = listForecast.filter((item: any) => {
            return new Date(item.dt).getTime() >= nowMs;
          });

          const result: DataWeatherResponse = {
            city: response.city,
            list: forecastNext,
          };
          setFormData((prev) => ({
            ...prev,
            latEnd: positionEnd.lat,
            lonEnd: positionEnd.lon,
          }));
          setWeatherEnd(result);
        } catch (error) {
          console.error("Error fetching weather data for end position:", error);
        }
      }
    })();
  }, [positionEnd]);

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
        maxWidth={false}
        slotProps={{
          paper: {
            sx: {
              width: "90vw",
              height: "90vh",
              padding: "3px",
              borderRadius: 3,
              boxShadow: "0 8px 24px rgba(16,24,40,0.12)",
            },
          },
        }}
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
            flex: mapOpen ? 7 : 1,
            p: 2,
            overflowY: "auto",
          }}
        >
          <DialogTitle>Create New Schedule</DialogTitle>
          <DialogContent
            sx={{
              pt: 2,
              display: "flex",
              flexDirection: "column",
              gap: 2,
            }}
          >
            <Paper sx={{ p: 2, height: "100%", width: "100%" }}>
              <TextField
                select
                fullWidth
                label="Type"
                value={formData.postType}
                onChange={(e) => {
                  const newType = e.target.value;
                  handleFormChange("postType", newType);
                  if (newType === "BUSINESS_SCHEDULE") {
                    setMapOpen(false);
                  } else if (newType === "TRAVEL_ITINERARY") {
                    setMapOpen(true);
                  }
                }}
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
              >
                <MenuItem value="BUSINESS_SCHEDULE">Business Schedule</MenuItem>
                <MenuItem value="TRAVEL_ITINERARY">Travel Itinerary</MenuItem>
              </TextField>
            </Paper>
            <Paper sx={{ p: 2, height: "100%", width: "100%" }}>
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
            </Paper>
            <Paper sx={{ p: 2, height: "100%", width: "100%" }}>
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
            </Paper>
            <Paper sx={{ p: 2, height: "100%", width: "100%" }}>
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
            </Paper>
            <Paper sx={{ p: 2, height: "100%", width: "100%" }}>
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
            </Paper>
            {weatherStart !== null && (
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
                  value={weatherStart.city.name}
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
                <ForecastList weather={weatherStart} />
              </Paper>
            )}
            {weatherEnd !== null && (
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
                  value={weatherEnd.city.name}
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
                <ForecastList weather={weatherEnd} />
              </Paper>
            )}
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
                !formData.content ||
                !formData.postType ||
                (formData.postType === "TRAVEL_ITINERARY" &&
                  (!positionStart || !positionEnd))
              }
            >
              {loading === false ? "Create" : ""}
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
            <div id="my-map" style={{ width: "100%", height: "100%" }} />
          </Box>
        )}
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
              gridTemplateColumns: { xs: "1fr", md: "2fr 1fr 1fr 1fr" },
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
            <FormControl fullWidth>
              <InputLabel>Type</InputLabel>
              <Select
                value={typeFilter}
                label="Type"
                onChange={(e) => setTypeFilter(e.target.value)}
              >
                <MenuItem value="BUSINESS_SCHEDULE">Business Schedule</MenuItem>
                <MenuItem value="TRAVEL_ITINERARY">Travel Itinerary</MenuItem>
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
          weatherStart={selectedSchedule?.startPosition}
          weatherEnd={selectedSchedule?.endPosition}
        />

        <ScheduleDelete
          open={deleteOpen}
          scheduleId={selectedSchedule?.id ?? ""}
          type={typeFilter}
          onClose={() => setDeleteOpen(false)}
          onDelete={handleDeleteSchedule}
        />

        <ScheduleEdit
          open={editOpen}
          schedule={selectedSchedule}
          onClose={() => setEditOpen(false)}
          onSave={handleEditSchedule}
          weatherStart={selectedSchedule?.startPosition}
          weatherEnd={selectedSchedule?.endPosition}
        />
      </Container>
    </>
  );
}

export default Schedules;
