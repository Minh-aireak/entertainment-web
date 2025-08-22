import * as React from "react";
import { Avatar, Badge, IconButton, Tooltip } from "@mui/material";
import CameraAltIcon from "@mui/icons-material/CameraAlt";

interface EditAvatarProps {
  src: string;
  size: number;
  onSelectFile: (file: File) => void;
}

export default function EditAvatar({
  src,
  size = 200,
  onSelectFile,
}: EditAvatarProps) {
  const inputId = React.useId();

  return (
    <Badge
      overlap="circular"
      anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
      badgeContent={
        <Tooltip title="Change Avatar">
          <IconButton
            component="label"
            htmlFor={inputId}
            size="small"
            sx={{
              bgcolor: "background.paper",
              boxShadow: 1,
              "&:hover": { bgcolor: "background.paper" },
              width: 35,
              height: 35,
            }}
          >
            <CameraAltIcon fontSize="large" />
            <input
              id={inputId}
              type="file"
              accept="image/*"
              hidden
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file && onSelectFile) onSelectFile(file);
              }}
            />
          </IconButton>
        </Tooltip>
      }
    >
      <Avatar src={src} sx={{ width: size, height: size, mb: 2, mt: 4 }} />
    </Badge>
  );
}
