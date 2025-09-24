import { useEffect, useRef, useState } from "react";
import { Box, IconButton } from "@mui/material";
import ForecastCard from "./ForecastCard";
import { type DataWeatherResponse } from "../../InterfaceDataType/DataType";
import { ArrowForwardIos, ArrowBackIos } from "@mui/icons-material";

export default function ForecastList({
  weather,
}: {
  weather?: DataWeatherResponse | null;
}) {
  const [page, setPage] = useState(0);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [cardsPerPage, setCardsPerPage] = useState<number>(1);

  useEffect(() => {
    const minCardWidth = 212;
    const handleWidth = (width: number) => {
      const possible = Math.max(1, Math.floor(width / minCardWidth));
      setCardsPerPage((prev) => {
        if (prev !== possible) return possible;
        return prev;
      });
    };

    const ro = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const w = entry.contentRect.width;
        handleWidth(w);
      }
    });

    if (containerRef.current) {
      ro.observe(containerRef.current);
      handleWidth(containerRef.current.clientWidth);
    }

    const onWinResize = () => {
      if (containerRef.current) handleWidth(containerRef.current.clientWidth);
    };
    window.addEventListener("resize", onWinResize);

    return () => {
      ro.disconnect();
      window.removeEventListener("resize", onWinResize);
    };
  }, []);

  const totalItems = weather?.list?.length ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalItems / cardsPerPage));
  const start = page * cardsPerPage;
  const end = start + cardsPerPage;
  const itemsToShow = weather?.list?.slice(start, end) ?? [];
  const isFirstPage = page <= 0;
  const isLastPage = page >= totalPages - 1;

  return (
    <Box sx={{ display: "flex", alignItems: "center", width: "100%" }}>
      <IconButton
        onClick={() => setPage((p) => Math.max(p - 1, 0))}
        disabled={isFirstPage}
      >
        <ArrowBackIos fontSize="small" />
      </IconButton>
      <Box
        ref={containerRef}
        sx={{
          display: "flex",
          overflowX: "auto",
          gap: 1,
          py: 1,
          flex: 1,
          justifyContent: "center",
        }}
      >
        {itemsToShow.map((f: any, i: number) => (
          <ForecastCard key={i} forecast={f} />
        ))}
      </Box>

      <IconButton
        onClick={() => setPage((p) => Math.min(p + 1, totalPages - 1))}
        disabled={isLastPage}
      >
        <ArrowForwardIos fontSize="small" />
      </IconButton>
    </Box>
  );
}
