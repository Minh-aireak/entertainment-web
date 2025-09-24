import React, { useEffect, useState, useRef } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Paper,
  TextField,
} from "@mui/material";
import { Save } from "@mui/icons-material";
import type {
  DataWeatherResponse,
  UpdatePostData,
  ScheduleResponse,
  LatLon,
} from "../../InterfaceDataType/DataType";
import { getDataWeather } from "../../services/WeatherService";
import * as L from "leaflet";
import "leaflet/dist/leaflet.css";
import "@geoapify/leaflet-address-search-plugin";
import "@geoapify/leaflet-address-search-plugin/dist/L.Control.GeoapifyAddressSearch.min.css";
import ForecastList from "../ForecastCard/ForecastList";

interface ScheduleEditProps {
  open: boolean;
  onClose: () => void;
  schedule: ScheduleResponse | null;
  onSave: (
    scheduleId: string,
    type: string,
    scheduleData: UpdatePostData
  ) => void;
  weatherStart?: DataWeatherResponse;
  weatherEnd?: DataWeatherResponse;
}

const ScheduleEdit: React.FC<ScheduleEditProps> = ({
  open,
  onClose,
  schedule,
  onSave,
  weatherStart,
  weatherEnd,
}) => {
  const [positionStart, setPositionStart] = useState<LatLon | null>(null);
  const [positionEnd, setPositionEnd] = useState<LatLon | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerStartRef = useRef<L.Marker | null>(null);
  const markerEndRef = useRef<L.Marker | null>(null);
  const [mapOpen, setMapOpen] = useState(false);
  const [scheduleData, setScheduleData] = useState<UpdatePostData>({
    title: "",
    content: "",
    startTime: new Date(),
    endTime: new Date(),
    latStart: "",
    lonStart: "",
    latEnd: "",
    lonEnd: "",
  });
  const handleChangeData = (field: keyof UpdatePostData, value: string) => {
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
        latStart: "",
        lonStart: "",
        latEnd: "",
        lonEnd: "",
      });
    }
  }, [schedule]);

  // useEffect(() => {
  //   if (weatherStart && weatherEnd) {
  //     setMapOpen(true);
  //   } else {
  //     setMapOpen(false);
  //   }
  // }, [weatherStart, weatherEnd]);

  // useEffect(() => {
  //   if (typeof window === "undefined") return;
  //   if (mapRef.current) return;
  //   if (!mapOpen) return;

  //   const myAPIKey = "c4dcc95ab99e41eea2d7e0424ed60066";
  //   const mapURLTemplate =
  //     "https://maps.geoapify.com/v1/tile/{mapStyle}/{z}/{x}/{y}{r}.png?apiKey={apiKey}";

  //   const map = L.map("my-map", {
  //     center: [21.0278, 105.8342],
  //     zoom: 10,
  //   });

  //   const tileUrl = mapURLTemplate
  //     .replace("{mapStyle}", "osm-bright-smooth")
  //     .replace("{apiKey}", myAPIKey);

  //   L.tileLayer(tileUrl, {
  //     maxZoom: 20,
  //     detectRetina: true,
  //   }).addTo(map);

  //   const addressStartSearchControl = (L.control as any).addressSearch(
  //     myAPIKey,
  //     {
  //       position: "topleft",
  //       placeholder: "Choose start location...",
  //       mapViewBias: true,
  //       resultCallback: (selected: any) => {
  //         if (selected && selected.lat && selected.lon) {
  //           setPositionStart({ lat: selected.lat, lon: selected.lon });
  //           if (markerStartRef.current) {
  //             map.removeLayer(markerStartRef.current);
  //           }
  //           const coloredIcon = L.icon({
  //             iconUrl:
  //               "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png",
  //             shadowUrl:
  //               "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png",
  //             iconSize: [25, 41],
  //             iconAnchor: [12, 41],
  //             popupAnchor: [1, -34],
  //             shadowSize: [41, 41],
  //           });
  //           markerStartRef.current = L.marker([selected.lat, selected.lon], {
  //             icon: coloredIcon,
  //           }).addTo(map);
  //           map.setView([selected.lat, selected.lon], 10);
  //         }
  //       },
  //     }
  //   );

  //   const addressEndSearchControl = (L.control as any).addressSearch(myAPIKey, {
  //     position: "topleft",
  //     placeholder: "Choose place to visit...",
  //     mapViewBias: true,
  //     resultCallback: (selected: any) => {
  //       if (selected && selected.lat && selected.lon) {
  //         setPositionEnd({ lat: selected.lat, lon: selected.lon });
  //         if (markerEndRef.current) {
  //           map.removeLayer(markerEndRef.current);
  //         }
  //         const coloredIcon = L.icon({
  //           iconUrl:
  //             "https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png",
  //           shadowUrl:
  //             "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png",
  //           iconSize: [25, 41],
  //           iconAnchor: [12, 41],
  //           popupAnchor: [1, -34],
  //           shadowSize: [41, 41],
  //         });
  //         markerEndRef.current = L.marker([selected.lat, selected.lon], {
  //           icon: coloredIcon,
  //         }).addTo(map);
  //         map.setView([selected.lat, selected.lon], 10);
  //       }
  //     },
  //   });

  //   map.addControl(addressStartSearchControl);
  //   map.addControl(addressEndSearchControl);

  //   return () => {
  //     if (markerStartRef.current) {
  //       markerStartRef.current.remove();
  //       setPositionStart(null);
  //       markerStartRef.current = null;
  //     }

  //     if (markerEndRef.current) {
  //       markerEndRef.current.remove();
  //       setPositionEnd(null);
  //       markerEndRef.current = null;
  //     }
  //   };
  // }, [mapOpen]);

  // useEffect(() => {
  //   (async () => {
  //     if (positionStart) {
  //       try {
  //         const response = await getDataWeather(positionStart);
  //         const listForecast = response.list;
  //         const nowMs = new Date().getTime();

  //         const forecastNext = listForecast.filter((item: any) => {
  //           return new Date(item.dt).getTime() >= nowMs;
  //         });

  //         const result: DataWeatherResponse = {
  //           list: forecastNext,
  //           city: response.city,
  //         };
  //         setScheduleData((prev) => ({
  //           ...prev,
  //           latStart: positionStart.lat,
  //           lonStart: positionStart.lon,
  //         }));
  //         // setWeatherStart(result);
  //       } catch (error) {
  //         console.error(
  //           "Error fetching weather data for start position:",
  //           error
  //         );
  //       }
  //     }
  //   })();
  // }, [positionStart]);

  // useEffect(() => {
  //   (async () => {
  //     if (positionEnd) {
  //       try {
  //         const response = await getDataWeather(positionEnd);
  //         const listForecast = response.list;
  //         const nowMs = new Date().getTime();

  //         const forecastNext = listForecast.filter((item: any) => {
  //           return new Date(item.dt).getTime() >= nowMs;
  //         });

  //         const result: DataWeatherResponse = {
  //           city: response.city,
  //           list: forecastNext,
  //         };
  //         setScheduleData((prev) => ({
  //           ...prev,
  //           latEnd: positionEnd.lat,
  //           lonEnd: positionEnd.lon,
  //         }));
  //         // setWeatherEnd(result);
  //       } catch (error) {
  //         console.error("Error fetching weather data for end position:", error);
  //       }
  //     }
  //   })();
  // }, [positionEnd]);

  return (
    <Dialog
      open={open}
      onClose={onClose}
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
          flex: 7,
          p: 2,
          overflowY: "auto",
        }}
      >
        <DialogTitle>Edit Schedule</DialogTitle>
        <DialogContent>
          <Box
            sx={{
              pt: 2,
              display: "flex",
              flexDirection: "column",
              gap: 2,
            }}
          >
            <Paper sx={{ p: 2, height: "100%", width: "100%" }}>
              <TextField
                autoFocus
                fullWidth
                value={scheduleData.title}
                onChange={(e) => handleChangeData("title", e.target.value)}
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
            <Paper sx={{ p: 2, height: "100%", width: "100%" }}>
              <TextField
                fullWidth
                type="datetime-local"
                value={scheduleData.startTime}
                onChange={(e) => handleChangeData("startTime", e.target.value)}
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
            <Paper sx={{ p: 2, height: "100%", width: "100%" }}>
              <TextField
                fullWidth
                type="datetime-local"
                value={scheduleData.endTime}
                onChange={(e) => handleChangeData("endTime", e.target.value)}
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
            <Paper sx={{ p: 2, height: "100%", width: "100%" }}>
              <TextField
                fullWidth
                value={scheduleData.content}
                onChange={(e) => handleChangeData("content", e.target.value)}
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
            {mapOpen && (
              <>
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
                    value={weatherStart?.city?.name ?? ""}
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
                    value={weatherEnd?.city?.name ?? ""}
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
              </>
            )}
          </Box>
        </DialogContent>
        <DialogActions sx={{ p: 3, pt: 0 }}>
          <Button onClick={onClose} variant="outlined">
            Close
          </Button>
          <Button
            onClick={() => {
              if (schedule !== null)
                onSave(schedule.id, schedule.postType, scheduleData);
            }}
            variant="contained"
            startIcon={<Save />}
          >
            Save changes
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
  );
};

export default ScheduleEdit;
