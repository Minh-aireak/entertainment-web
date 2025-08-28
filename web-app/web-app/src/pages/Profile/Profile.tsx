import { useEffect, useState, useRef } from "react";
import { Grid, Typography, Button, Paper, TextField, Box } from "@mui/material";
import EditIcon from "@mui/icons-material/Edit";
import SaveIcon from "@mui/icons-material/Save";
import classNames from "classnames/bind";
import styles from "./Profile.module.scss";
import dayjs from "dayjs";
import EditAvatar from "./EditAvatar";
import { isAuthenticated } from "../../features/hooks/useAuthApi";
import { CustomAlertSnackbar } from "../../components/CustomAlertSnackbar";
import {
  getMyInfo,
  updateProfile,
  uploadAvatar,
} from "../../services/userService";
import type { UserProfileResponse } from "../../InterfaceDataType/DataTypeResponse";

type ResultUserProfileResponse = UserProfileResponse["result"];
const cx = classNames.bind(styles);

const Profile = () => {
  const [userData, setUserData] = useState<ResultUserProfileResponse>({
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

  const handleEditClick = () => {
    setTempData({ ...userData });
    setIsEditing(true);
  };

  const handleSaveClick = async () => {
    try {
      const response = await updateProfile(tempData);
      setUserData({ ...tempData });
      setSnackbarMessage(response.message);
      setSeverity(true);
      setIsEditing(false);
    } catch (error: any) {
      setSeverity(false);
      setSnackbarMessage(error.response.data.message);
    }
    setSnackbarOpen(true);
  };

  const handleChange = (e: any) => {
    setTempData({
      ...tempData,
      [e.target.name]: e.target.value,
    });
  };

  // const handleNewAvatar = async (file: File) => {
  //   try {
  //     const newUrl = await uploadAvatar(file);
  //   } catch (error: any) {}
  // };

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const response = await getMyInfo();

        setUserData({
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
        setSnackbarMessage(error.response.data.message);
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
              {/* <EditAvatar src={userData.avatar} onSelectFile={handleNewAvatar} /> */}
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