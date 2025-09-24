import { useEffect, useState, useRef } from "react";
import {
  Grid,
  Typography,
  Button,
  Paper,
  TextField,
  Box,
  Avatar,
} from "@mui/material";
import axios from "axios";
import EditIcon from "@mui/icons-material/Edit";
import SaveIcon from "@mui/icons-material/Save";
import PhotoCameraIcon from "@mui/icons-material/PhotoCamera";
import classNames from "classnames/bind";
import styles from "./Profile.module.scss";
import dayjs from "dayjs";
import { isAuthenticated } from "../../features/hooks/useAuthApi";
import { CustomAlertSnackbar } from "../../components/CustomAlertSnackbar";
import { Loading } from "../../components/Loading";
import {
  getMyInfo,
  updateProfile,
  uploadAvatar,
} from "../../services/UserService";
import type { UserProfileResponse } from "../../InterfaceDataType/DataType";

type ResultUserProfileResponse = UserProfileResponse["result"];
const cx = classNames.bind(styles);

const Profile = () => {
  const [userData, setUserData] = useState<ResultUserProfileResponse>({
    userId: "",
    username: "",
    email: "",
    firstName: "",
    lastName: "",
    dob: new Date(""),
    phoneNumber: "",
    city: "",
    joinDate: new Date(""),
    avatar: "",
  });
  const [isEditing, setIsEditing] = useState(false);
  const [tempData, setTempData] = useState({ ...userData });
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [severity, setSeverity] = useState(true);
  const [loading, setLoading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const handleEditClick = () => {
    setTempData({ ...userData });
    setIsEditing(true);
  };

  const handleSaveClick = async () => {
    try {
      setLoading(true);
      const response = await updateProfile(tempData);
      setUserData({ ...tempData });
      setSnackbarMessage(response.message);
      setSeverity(true);
      setIsEditing(false);
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
      setSnackbarOpen(true);
    }
  };

  const handleChange = (e: any) => {
    setTempData({
      ...tempData,
      [e.target.name]: e.target.value,
    });
  };

  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  const handleUploadAvatar = async (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = event.currentTarget.files?.[0];
    if (!file) return;

    if (!file.type.match("image.*")) {
      setSnackbarMessage("Please select an image file");
      setSeverity(false);
      setSnackbarOpen(true);
      return;
    }
    try {
      setLoading(true);
      const formData = new FormData();
      formData.append("file", file);

      const response = await uploadAvatar(formData);

      const imageUrl = response.result.avatar;
      setUserData({ ...userData, avatar: imageUrl });
      setSnackbarMessage("Avatar updated successfully!");
      setSeverity(true);
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
    const fetchProfile = async () => {
      try {
        const response = await getMyInfo();

        setUserData({
          userId: response.result.userId ?? "",
          username: response.result.username ?? "",
          email: response.result.email ?? "",
          firstName: response.result.firstName ?? "",
          lastName: response.result.lastName ?? "",
          dob: response.result.dob ?? "",
          phoneNumber: response.result.phoneNumber ?? "",
          city: response.result.city ?? "",
          joinDate: response.result.joinDate ?? "",
          avatar: response.result.avatar ?? "",
        });
      } catch (error: any) {
        let messageToShow = error.message;
        if (axios.isAxiosError(error)) {
          if (error.response) {
            messageToShow = error.response.data.message;
          }
        }
        setSnackbarMessage(messageToShow);
        setSeverity(false);
        setSnackbarOpen(true);
      }
    };

    if (isAuthenticated()) {
      fetchProfile();
    }
  }, []);

  return (
    <>
      {loading && <Loading loading={loading} />}
      <CustomAlertSnackbar
        open={snackbarOpen}
        message={snackbarMessage}
        severity={severity ? "success" : "error"}
        onClose={() => setSnackbarOpen(false)}
      />
      <Paper
        elevation={7}
        sx={{ padding: 5, maxWidth: 900, margin: "auto", mt: 5 }}
      >
        <Grid container spacing={7}>
          <Grid size={{ xs: 12, md: 4, sm: 6 }}>
            <Box display="flex" flexDirection="column" alignItems="center">
              <Box sx={{ position: "relative", mt: 2, mb: 2 }}>
                <Avatar
                  src={userData.avatar}
                  sx={{
                    width: 200,
                    height: 200,
                    fontSize: 60,
                    cursor: "pointer",
                    transition: "opacity 0.3s",
                    "&:hover": {
                      opacity: 0.8,
                    },
                  }}
                  onClick={handleAvatarClick}
                ></Avatar>
                <Box
                  sx={{
                    position: "absolute",
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    opacity: 0,
                    transition: "opacity 0.3s",
                    borderRadius: "50%",
                    backgroundColor: "rgba(0, 0, 0, 0.4)",
                    "&:hover": {
                      opacity: 1,
                    },
                    cursor: "pointer",
                  }}
                  onClick={handleAvatarClick}
                >
                  <PhotoCameraIcon sx={{ color: "white", fontSize: 39 }} />
                </Box>
                <input
                  type="file"
                  accept="image/*"
                  ref={fileInputRef}
                  style={{ display: "none" }}
                  onChange={handleUploadAvatar}
                />
              </Box>
              <Typography>
                Joined: {dayjs(userData.joinDate).format("DD/MM/YYYY")}
              </Typography>
            </Box>
          </Grid>

          <Grid size={{ xs: 12, md: 8, sm: 6 }}>
            <Typography
              variant="h4"
              gutterBottom
              sx={{
                fontWeight: "bold",
                textAlign: "center",
              }}
            >
              Profile
            </Typography>

            {isEditing ? (
              <Box>
                <TextField
                  fullWidth
                  label="Username"
                  name="username"
                  value={tempData.username}
                  margin="normal"
                  variant="outlined"
                  slotProps={{ htmlInput: { readOnly: true } }}
                />
                <TextField
                  fullWidth
                  label="Email"
                  name="email"
                  value={tempData.email}
                  onChange={handleChange}
                  margin="normal"
                  variant="outlined"
                  slotProps={{ htmlInput: { readOnly: true } }}
                />
                <TextField
                  fullWidth
                  label="First Name"
                  name="firstName"
                  value={tempData.firstName}
                  onChange={handleChange}
                  margin="normal"
                  variant="outlined"
                />
                <TextField
                  fullWidth
                  label="Last Name"
                  name="lastName"
                  value={tempData.lastName}
                  onChange={handleChange}
                  margin="normal"
                  variant="outlined"
                />
                <TextField
                  fullWidth
                  label="Phone number"
                  name="phoneNumber"
                  value={tempData.phoneNumber}
                  onChange={handleChange}
                  margin="normal"
                  variant="outlined"
                />
                <TextField
                  fullWidth
                  label="City"
                  name="city"
                  value={tempData.city}
                  onChange={handleChange}
                  margin="normal"
                  variant="outlined"
                />
              </Box>
            ) : (
              <Box marginBottom={2}>
                <Typography variant="h6" className={cx("space-profile-info")}>
                  <strong>Username:</strong>
                  {userData.username}
                </Typography>
                <Typography variant="h6" className={cx("space-profile-info")}>
                  <strong>Email:</strong> {userData.email}
                </Typography>
                <Typography variant="h6" className={cx("space-profile-info")}>
                  <strong>Full Name:</strong>{" "}
                  {userData.firstName + " " + userData.lastName}
                </Typography>
                <Typography variant="h6" className={cx("space-profile-info")}>
                  <strong>Phone Number:</strong> {userData.phoneNumber}
                </Typography>
                <Typography variant="h6" className={cx("space-profile-info")}>
                  <strong>City:</strong> {userData.city}
                </Typography>
              </Box>
            )}

            <Box sx={{ display: "flex", gap: 2 }}>
              {isEditing ? (
                <Button
                  variant="contained"
                  color="primary"
                  startIcon={<SaveIcon />}
                  onClick={handleSaveClick}
                  sx={{ px: 4, height: 40 }}
                >
                  Save
                </Button>
              ) : (
                <Button
                  variant="contained"
                  color="primary"
                  startIcon={<EditIcon />}
                  onClick={handleEditClick}
                  sx={{ px: 4, height: 40 }}
                >
                  Edit
                </Button>
              )}

              {isEditing && (
                <Button
                  variant="outlined"
                  color="error"
                  onClick={() => setIsEditing(false)}
                  sx={{ px: 4, height: 40 }}
                >
                  Cancel
                </Button>
              )}
            </Box>
          </Grid>
        </Grid>
      </Paper>
    </>
  );
};

export default Profile;
