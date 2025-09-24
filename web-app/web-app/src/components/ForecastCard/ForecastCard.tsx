import React from "react";
import { Card, CardContent, Typography, Box, Avatar } from "@mui/material";
import {type ListForecast } from "../../InterfaceDataType/DataType";

const ForecastCard: React.FC<{ forecast: ListForecast}> = ({
  forecast,
}) => {
  const getDateMs = () => {
    return Date.parse(forecast.dt_txt);
  };
  const date = new Date(getDateMs());
  const timeLabel = date.toLocaleString();

  const iconCode = forecast.weather?.[0]?.icon;
  const iconUrl = iconCode
    ? `https://openweathermap.org/img/wn/${iconCode}@2x.png`
    : undefined;

  const rainVolume = forecast.rain?.rain ?? "0";

  const pop =
    typeof forecast.pop === "number" ? Math.round(forecast.pop * 100) : 0;

  return (
    <Card
      variant="outlined"
      sx={{
        width: 210,
        flex: "0 0 auto",
        display: "flex",
        flexDirection: "column",
        borderRadius: 3,
        border: "groove",
        boxShadow: "0 2px 8px rgba(0, 0, 0, 0.1)",
        bgcolor: "background.paper",
      }}
    >
      <CardContent sx={{ display: "flex", gap: 1, alignItems: "center" }}>
        <Box>
          <Typography variant="caption" color="text.secondary">
            {timeLabel}
          </Typography>
        </Box>
      </CardContent>

      <CardContent sx={{ pt: 0, pb: 2 }}>
        <Box sx={{ display: "flex", alignItems: "center" }}>
          {iconUrl ? (
            <Avatar
              src={iconUrl}
              alt={forecast.weather?.[0]?.description ?? "weather"}
              sx={{ width: 50, height: 50 }}
            />
          ) : null}
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h6" component="div">
              {Math.round(forecast.main.temp)}°C - {forecast.weather?.[0]?.main}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Feels like {Math.round(forecast.main.feels_like)}°C
            </Typography>
          </Box>
        </Box>

        <Box sx={{ display: "flex", justifyContent: "space-between", mt: 1 }}>
          <Typography variant="caption">Humidity</Typography>
          <Typography variant="caption">{forecast.main.humidity}%</Typography>
        </Box>

        <Box sx={{ display: "flex", justifyContent: "space-between", mt: 0.5 }}>
          <Typography variant="caption">Wind</Typography>
          <Typography variant="caption">
            {forecast.wind?.speed ?? "-"} m/s
          </Typography>
        </Box>

        <Box sx={{ display: "flex", justifyContent: "space-between", mt: 0.5 }}>
          <Typography variant="caption">Clouds</Typography>
          <Typography variant="caption">
            {forecast.clouds?.all ?? "-"}%
          </Typography>
        </Box>

        <Box sx={{ display: "flex", justifyContent: "space-between", mt: 0.5 }}>
          <Typography variant="caption">Precip prob</Typography>
          <Typography variant="caption">{pop}%</Typography>
        </Box>

        <Box sx={{ display: "flex", justifyContent: "space-between", mt: 0.5 }}>
          <Typography variant="caption">Precip</Typography>
          <Typography variant="caption">{rainVolume} mm</Typography>
        </Box>
      </CardContent>
    </Card>
  );
};

export default ForecastCard;
