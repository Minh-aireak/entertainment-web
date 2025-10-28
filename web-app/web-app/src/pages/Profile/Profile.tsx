import { useEffect, useState, useRef } from "react";
import {
  Grid,
  Typography,
  Button,
  Paper,
  TextField,
  Box,
  Avatar,
  Card,
  Divider,
  Chip,
  Container,
  Fade,
  Zoom,
} from "@mui/material";
import axios from "axios";
import EditIcon from "@mui/icons-material/Edit";
import SaveIcon from "@mui/icons-material/Save";
import PhotoCameraIcon from "@mui/icons-material/PhotoCamera";
import PersonIcon from "@mui/icons-material/Person";
import EmailIcon from "@mui/icons-material/Email";
import PhoneIcon from "@mui/icons-material/Phone";
import LocationCityIcon from "@mui/icons-material/LocationCity";
import BadgeIcon from "@mui/icons-material/Badge";
import CalendarTodayIcon from "@mui/icons-material/CalendarToday";
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
    displayName: "",
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
          displayName:
            response.result.displayName ?? response.result.username ?? "",
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

      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Fade in timeout={800}>
          <Box>
            <Card
              elevation={20}
              sx={{
                borderRadius: 4,
                height: "100%",
                display: "flex",
                p: 0,
              }}
            >
                <Box
                  sx={{
                    display: "grid",
                    gridTemplateColumns: { xs: "1fr", md: "1fr 2fr" },
                    minHeight: 600,
                    flex: 1,
                  }}
                >
                  <Box
                    sx={{
                      background:
                        "linear-gradient(45deg, #667eea 30%, #764ba2 90%)",
                      p: 4,
                      display: "flex",
                      flexDirection: "column",
                      alignItems: "center",
                      justifyContent: "center",
                    }}
                  >
                    <Zoom in timeout={1000}>
                      <Box sx={{ position: "relative", mb: 3 }}>
                        <Avatar
                          src={userData.avatar}
                          sx={{
                            width: 180,
                            height: 180,
                            fontSize: 60,
                            cursor: "pointer",
                            transition: "all 0.3s ease",
                            border: "6px solid rgba(255,255,255,0.3)",
                            boxShadow: "0 8px 32px rgba(0,0,0,0.3)",
                            "&:hover": {
                              transform: "scale(1.05)",
                              boxShadow: "0 12px 40px rgba(0,0,0,0.4)",
                            },
                          }}
                          onClick={handleAvatarClick}
                        />
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
                            backgroundColor: "rgba(0, 0, 0, 0.5)",
                            "&:hover": {
                              opacity: 1,
                            },
                            cursor: "pointer",
                          }}
                          onClick={handleAvatarClick}
                        >
                          <PhotoCameraIcon
                            sx={{ color: "white", fontSize: 40 }}
                          />
                        </Box>
                        <input
                          type="file"
                          accept="image/*"
                          ref={fileInputRef}
                          style={{ display: "none" }}
                          onChange={handleUploadAvatar}
                        />
                      </Box>
                    </Zoom>

                    <Typography
                      variant="h5"
                      sx={{
                        color: "white",
                        fontWeight: "bold",
                        mb: 1,
                        textAlign: "center",
                      }}
                    >
                      {userData.displayName || userData.username}
                    </Typography>

                    <Chip
                      icon={<CalendarTodayIcon />}
                      label={`Joined ${dayjs(userData.joinDate).format(
                        "MMM YYYY"
                      )}`}
                      sx={{
                        backgroundColor: "rgba(255,255,255,0.2)",
                        color: "white",
                        fontWeight: "bold",
                      }}
                    />
                  </Box>

                  <Box sx={{ p: 4, display: "flex", flexDirection: "column" }}>
                    {isEditing ? (
                      <Fade in timeout={500}>
                        <Box>
                          <Typography
                            variant="h5"
                            sx={{
                              fontWeight: "bold",
                              mb: 3,
                              color: "#333",
                              textAlign: "center",
                            }}
                          >
                            Edit Profile
                          </Typography>

                          <Grid container spacing={2}>
                            <Grid size={{ xs: 12, sm: 6 }}>
                              <TextField
                                fullWidth
                                label="Username"
                                name="username"
                                value={tempData.username}
                                margin="normal"
                                variant="outlined"
                                slotProps={{ htmlInput: { readOnly: true } }}
                                sx={{
                                  "& .MuiOutlinedInput-root": {
                                    borderRadius: 2,
                                  },
                                }}
                              />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6 }}>
                              <TextField
                                fullWidth
                                label="Display Name"
                                name="displayName"
                                value={tempData.displayName}
                                onChange={handleChange}
                                margin="normal"
                                variant="outlined"
                                placeholder="Display name"
                                sx={{
                                  "& .MuiOutlinedInput-root": {
                                    borderRadius: 2,
                                  },
                                }}
                              />
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                              <TextField
                                fullWidth
                                label="Email"
                                name="email"
                                value={tempData.email}
                                onChange={handleChange}
                                margin="normal"
                                variant="outlined"
                                slotProps={{ htmlInput: { readOnly: true } }}
                                sx={{
                                  "& .MuiOutlinedInput-root": {
                                    borderRadius: 2,
                                  },
                                }}
                              />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6 }}>
                              <TextField
                                fullWidth
                                label="First Name"
                                name="firstName"
                                value={tempData.firstName}
                                onChange={handleChange}
                                margin="normal"
                                variant="outlined"
                                sx={{
                                  "& .MuiOutlinedInput-root": {
                                    borderRadius: 2,
                                  },
                                }}
                              />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6 }}>
                              <TextField
                                fullWidth
                                label="Last Name"
                                name="lastName"
                                value={tempData.lastName}
                                onChange={handleChange}
                                margin="normal"
                                variant="outlined"
                                sx={{
                                  "& .MuiOutlinedInput-root": {
                                    borderRadius: 2,
                                  },
                                }}
                              />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6 }}>
                              <TextField
                                fullWidth
                                label="Phone Number"
                                name="phoneNumber"
                                value={tempData.phoneNumber}
                                onChange={handleChange}
                                margin="normal"
                                variant="outlined"
                                sx={{
                                  "& .MuiOutlinedInput-root": {
                                    borderRadius: 2,
                                  },
                                }}
                              />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6 }}>
                              <TextField
                                fullWidth
                                label="City"
                                name="city"
                                value={tempData.city}
                                onChange={handleChange}
                                margin="normal"
                                variant="outlined"
                                sx={{
                                  "& .MuiOutlinedInput-root": {
                                    borderRadius: 2,
                                  },
                                }}
                              />
                            </Grid>
                          </Grid>
                        </Box>
                      </Fade>
                    ) : (
                      <Fade in timeout={500}>
                        <Box>
                          <Typography
                            variant="h5"
                            sx={{
                              fontWeight: "bold",
                              mb: 3,
                              color: "#333",
                              textAlign: "center",
                            }}
                          >
                            Profile Information
                          </Typography>

                          <Box sx={{ space: 2 }}>
                            {/* Username */}
                            <Box
                              sx={{
                                display: "flex",
                                alignItems: "center",
                                p: 2,
                                mb: 2,
                                backgroundColor: "#f8f9fa",
                                borderRadius: 2,
                                border: "1px solid #e9ecef",
                              }}
                            >
                              <PersonIcon sx={{ color: "#667eea", mr: 2 }} />
                              <Box>
                                <Typography
                                  variant="body2"
                                  color="textSecondary"
                                >
                                  Username
                                </Typography>
                                <Typography variant="h6" fontWeight="bold">
                                  {userData.username}
                                </Typography>
                              </Box>
                            </Box>

                            {/* Display Name */}
                            <Box
                              sx={{
                                display: "flex",
                                alignItems: "center",
                                p: 2,
                                mb: 2,
                                backgroundColor: "#f8f9fa",
                                borderRadius: 2,
                                border: "1px solid #e9ecef",
                              }}
                            >
                              <BadgeIcon sx={{ color: "#667eea", mr: 2 }} />
                              <Box>
                                <Typography
                                  variant="body2"
                                  color="textSecondary"
                                >
                                  Display Name
                                </Typography>
                                <Typography variant="h6" fontWeight="bold">
                                  {userData.displayName || userData.username}
                                </Typography>
                              </Box>
                            </Box>

                            {/* Email */}
                            <Box
                              sx={{
                                display: "flex",
                                alignItems: "center",
                                p: 2,
                                mb: 2,
                                backgroundColor: "#f8f9fa",
                                borderRadius: 2,
                                border: "1px solid #e9ecef",
                              }}
                            >
                              <EmailIcon sx={{ color: "#667eea", mr: 2 }} />
                              <Box>
                                <Typography
                                  variant="body2"
                                  color="textSecondary"
                                >
                                  Email
                                </Typography>
                                <Typography variant="h6" fontWeight="bold">
                                  {userData.email}
                                </Typography>
                              </Box>
                            </Box>

                            {/* Full Name */}
                            <Box
                              sx={{
                                display: "flex",
                                alignItems: "center",
                                p: 2,
                                mb: 2,
                                backgroundColor: "#f8f9fa",
                                borderRadius: 2,
                                border: "1px solid #e9ecef",
                              }}
                            >
                              <PersonIcon sx={{ color: "#667eea", mr: 2 }} />
                              <Box>
                                <Typography
                                  variant="body2"
                                  color="textSecondary"
                                >
                                  Full Name
                                </Typography>
                                <Typography variant="h6" fontWeight="bold">
                                  {userData.firstName + " " + userData.lastName}
                                </Typography>
                              </Box>
                            </Box>

                            {/* Phone */}
                            <Box
                              sx={{
                                display: "flex",
                                alignItems: "center",
                                p: 2,
                                mb: 2,
                                backgroundColor: "#f8f9fa",
                                borderRadius: 2,
                                border: "1px solid #e9ecef",
                              }}
                            >
                              <PhoneIcon sx={{ color: "#667eea", mr: 2 }} />
                              <Box>
                                <Typography
                                  variant="body2"
                                  color="textSecondary"
                                >
                                  Phone Number
                                </Typography>
                                <Typography variant="h6" fontWeight="bold">
                                  {userData.phoneNumber}
                                </Typography>
                              </Box>
                            </Box>

                            {/* City */}
                            <Box
                              sx={{
                                display: "flex",
                                alignItems: "center",
                                p: 2,
                                mb: 2,
                                backgroundColor: "#f8f9fa",
                                borderRadius: 2,
                                border: "1px solid #e9ecef",
                              }}
                            >
                              <LocationCityIcon
                                sx={{ color: "#667eea", mr: 2 }}
                              />
                              <Box>
                                <Typography
                                  variant="body2"
                                  color="textSecondary"
                                >
                                  City
                                </Typography>
                                <Typography variant="h6" fontWeight="bold">
                                  {userData.city}
                                </Typography>
                              </Box>
                            </Box>
                          </Box>
                        </Box>
                      </Fade>
                    )}

                    <Divider sx={{ my: 3 }} />

                    {/* Action Buttons */}
                    <Box
                      sx={{ display: "flex", gap: 2, justifyContent: "center" }}
                    >
                      {isEditing ? (
                        <>
                          <Button
                            variant="contained"
                            startIcon={<SaveIcon />}
                            onClick={handleSaveClick}
                            sx={{
                              px: 4,
                              py: 1.5,
                              borderRadius: 3,
                              background:
                                "linear-gradient(45deg, #667eea 30%, #764ba2 90%)",
                              boxShadow: "0 4px 20px rgba(102, 126, 234, 0.4)",
                              "&:hover": {
                                transform: "translateY(-2px)",
                                boxShadow:
                                  "0 6px 25px rgba(102, 126, 234, 0.6)",
                              },
                              transition: "all 0.3s ease",
                            }}
                          >
                            Save Changes
                          </Button>
                          <Button
                            variant="outlined"
                            onClick={() => setIsEditing(false)}
                            sx={{
                              px: 4,
                              py: 1.5,
                              borderRadius: 3,
                              borderColor: "#667eea",
                              color: "#667eea",
                              "&:hover": {
                                backgroundColor: "rgba(102, 126, 234, 0.1)",
                                transform: "translateY(-2px)",
                              },
                              transition: "all 0.3s ease",
                            }}
                          >
                            Cancel
                          </Button>
                        </>
                      ) : (
                        <Button
                          variant="contained"
                          startIcon={<EditIcon />}
                          onClick={handleEditClick}
                          sx={{
                            px: 4,
                            py: 1.5,
                            borderRadius: 3,
                            background:
                              "linear-gradient(45deg, #667eea 30%, #764ba2 90%)",
                            boxShadow: "0 4px 20px rgba(102, 126, 234, 0.4)",
                            "&:hover": {
                              transform: "translateY(-2px)",
                              boxShadow: "0 6px 25px rgba(102, 126, 234, 0.6)",
                            },
                            transition: "all 0.3s ease",
                          }}
                        >
                          Edit Profile
                        </Button>
                      )}
                    </Box>
                  </Box>
                </Box>
            </Card>
          </Box>
        </Fade>
      </Container>
    </>
  );
};

export default Profile;
