import React, { useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Grid,
  LinearProgress,
  Paper,
  TextField,
  Typography,
} from '@mui/material';
import { ArrowBack, CloudUpload, Movie } from '@mui/icons-material';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { filmService } from '../../api/filmService';
import { useEpisodeUpload } from '../../contexts/episodeUploadContextValue';
import type { EpisodeResponse } from '../../models';
import { readVideoMetadata, type VideoMetadata } from '../../utils/videoMetadata';

interface EpisodeUploadLocationState {
  episode?: EpisodeResponse;
  newUpload?: boolean;
}

const EpisodeUpload: React.FC = () => {
  const { filmId } = useParams<{ filmId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { job, isBusy, startUpload, clearJob } = useEpisodeUpload();
  const locationState = location.state as EpisodeUploadLocationState | null;
  const routeEpisode = locationState?.episode ?? null;
  const [startedHere, setStartedHere] = useState(false);

  const candidateJob = job?.filmId === filmId ? job : null;
  const activeJob = locationState?.newUpload && !startedHere
    ? null
    : routeEpisode
      ? candidateJob?.episodeId === routeEpisode.id ? candidateJob : null
      : candidateJob;

  const [editingEpisode] = useState<EpisodeResponse | null>(() => routeEpisode);
  const [formData, setFormData] = useState(() => ({
    seasonNumber: routeEpisode?.seasonNumber ?? activeJob?.seasonNumber ?? 1,
    episodeNumber: routeEpisode?.episodeNumber ?? activeJob?.episodeNumber ?? 1,
    title: routeEpisode?.title ?? activeJob?.title ?? '',
  }));
  const [file, setFile] = useState<File | null>(() => activeJob?.file ?? null);
  const [metadata, setMetadata] = useState<VideoMetadata | null>(() => activeJob ? {
    durationSeconds: activeJob.durationSeconds,
    durationMinutes: activeJob.durationMinutes,
    formattedDuration: activeJob.formattedDuration,
  } : null);
  const [readingMetadata, setReadingMetadata] = useState(false);
  const [savingDetails, setSavingDetails] = useState(false);
  const metadataRequestRef = useRef(0);

  const isEdit = Boolean(editingEpisode || activeJob?.mode === 'edit');
  const episodeId = editingEpisode?.id ?? activeJob?.episodeId;
  const jobIsBusy = activeJob?.status === 'uploading' || activeJob?.status === 'saving';
  const anotherJobIsBusy = isBusy && !jobIsBusy;
  const fieldsDisabled = Boolean(jobIsBusy || savingDetails || activeJob?.status === 'success');

  const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setFormData((current) => ({
      ...current,
      [name]: name === 'title' ? value : Math.max(1, Number.parseInt(value, 10) || 1),
    }));
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = event.target.files?.[0];
    if (!selectedFile) return;

    const requestId = metadataRequestRef.current + 1;
    metadataRequestRef.current = requestId;
    setFile(selectedFile);
    setMetadata(null);
    setReadingMetadata(true);

    try {
      const detectedMetadata = await readVideoMetadata(selectedFile);
      if (metadataRequestRef.current === requestId) setMetadata(detectedMetadata);
    } catch (error: unknown) {
      if (metadataRequestRef.current !== requestId) return;
      setFile(null);
      toast.error(error instanceof Error ? error.message : 'Không đọc được metadata video');
    } finally {
      if (metadataRequestRef.current === requestId) setReadingMetadata(false);
    }
  };

  const returnToEpisodeManagement = () => navigate('/admin?tab=episodes');

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();

    if (!filmId) {
      toast.error('ID phim không hợp lệ');
      return;
    }
    if (anotherJobIsBusy || jobIsBusy) {
      toast.error('Vui lòng chờ tác vụ upload hiện tại hoàn tất');
      return;
    }

    if (isEdit && !file) {
      if (!editingEpisode || !episodeId) {
        toast.error('Không tìm thấy thông tin tập phim cần chỉnh sửa');
        return;
      }

      setSavingDetails(true);
      try {
        const response = await filmService.updateEpisode(episodeId, {
          ...formData,
          videoFileId: editingEpisode.videoFileId,
          durationMinutes: editingEpisode.durationMinutes,
          filmId,
        });
        if (response.code !== 1000) throw new Error(response.message || 'Cập nhật tập phim thất bại');
        toast.success('Cập nhật tập phim thành công');
        returnToEpisodeManagement();
      } catch (error: unknown) {
        toast.error(error instanceof Error ? error.message : 'Cập nhật tập phim thất bại');
      } finally {
        setSavingDetails(false);
      }
      return;
    }

    if (!file || !metadata) {
      toast.error('Vui lòng chọn file video hợp lệ');
      return;
    }

    setStartedHere(true);
    await startUpload({
      mode: isEdit ? 'edit' : 'create',
      episodeId,
      filmId,
      ...formData,
      file,
      durationSeconds: metadata.durationSeconds,
      formattedDuration: metadata.formattedDuration,
    });
  };

  const handleFinish = () => {
    clearJob();
    returnToEpisodeManagement();
  };

  const handleCancelFailedUpload = () => {
    clearJob();
    setFile(null);
    setMetadata(null);
    setStartedHere(false);
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Button startIcon={<ArrowBack />} onClick={returnToEpisodeManagement} sx={{ mb: 2 }}>
        Quay lại quản lý tập phim
      </Button>

      <Paper elevation={3} sx={{ p: { xs: 2.5, sm: 4 }, borderRadius: 3 }}>
        <Box sx={{ mb: 4, textAlign: 'center' }}>
          <Movie color="primary" sx={{ fontSize: 44, mb: 1 }} />
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            {isEdit ? 'Chỉnh sửa tập phim' : 'Upload tập phim mới'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            Thời lượng được đọc tự động từ file. Video 20:45 sẽ được lưu là 20 phút.
          </Typography>
        </Box>

        {anotherJobIsBusy && (
          <Alert severity="warning" sx={{ mb: 3 }}>
            Một tập phim khác đang được upload. Bạn có thể mở lại từ biểu tượng upload ở góc phải màn hình.
          </Alert>
        )}
        {activeJob?.status === 'success' && (
          <Alert
            severity="success"
            sx={{ mb: 3 }}
            action={<Button color="inherit" onClick={handleFinish}>Hoàn tất</Button>}
          >
            {isEdit ? 'Video mới và thông tin tập phim đã được cập nhật.' : 'Tập phim đã được tạo thành công.'}
          </Alert>
        )}
        {activeJob?.status === 'error' && (
          <Alert
            severity="error"
            sx={{ mb: 3 }}
            action={<Button color="inherit" onClick={handleCancelFailedUpload}>Hủy thao tác</Button>}
          >
            {activeJob.error || 'Upload thất bại. File và thông tin vẫn được giữ để bạn thử lại.'}
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                fullWidth
                label="Mùa (Season)"
                name="seasonNumber"
                type="number"
                slotProps={{ htmlInput: { min: 1 } }}
                value={formData.seasonNumber}
                onChange={handleInputChange}
                disabled={fieldsDisabled}
                required
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                fullWidth
                label="Tập số (Episode)"
                name="episodeNumber"
                type="number"
                slotProps={{ htmlInput: { min: 1 } }}
                value={formData.episodeNumber}
                onChange={handleInputChange}
                disabled={fieldsDisabled}
                required
              />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <TextField
                fullWidth
                label="Tiêu đề tập phim"
                name="title"
                value={formData.title}
                onChange={handleInputChange}
                disabled={fieldsDisabled}
              />
            </Grid>

            <Grid size={{ xs: 12 }}>
              <Box
                component="label"
                sx={{
                  display: 'block',
                  border: '2px dashed',
                  borderColor: file ? 'primary.main' : 'divider',
                  borderRadius: 2,
                  p: 4,
                  textAlign: 'center',
                  cursor: fieldsDisabled ? 'default' : 'pointer',
                  opacity: fieldsDisabled ? 0.65 : 1,
                  '&:hover': fieldsDisabled ? undefined : { borderColor: 'primary.main', bgcolor: 'action.hover' },
                }}
              >
                <input
                  type="file"
                  hidden
                  accept="video/*"
                  onChange={handleFileChange}
                  disabled={fieldsDisabled}
                />
                {readingMetadata ? <CircularProgress size={46} /> : <CloudUpload sx={{ fontSize: 52, color: 'primary.main' }} />}
                <Typography variant="h6" sx={{ mt: 1 }}>
                  {file ? file.name : isEdit ? 'Chọn video mới để thay thế (không bắt buộc)' : 'Chọn file video'}
                </Typography>
                {file && (
                  <Typography variant="caption" color="text.secondary">
                    {(file.size / (1024 * 1024)).toFixed(2)} MB
                  </Typography>
                )}
                {metadata && (
                  <Box sx={{ mt: 2, display: 'flex', gap: 1, justifyContent: 'center', flexWrap: 'wrap' }}>
                    <Chip color="success" label={`Thời lượng file: ${metadata.formattedDuration}`} />
                    <Chip variant="outlined" label={`Sẽ lưu: ${metadata.durationMinutes} phút`} />
                  </Box>
                )}
                {!file && isEdit && editingEpisode && (
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    Giữ video hiện tại · {editingEpisode.durationMinutes} phút
                  </Typography>
                )}
              </Box>
            </Grid>

            {activeJob && (activeJob.status === 'uploading' || activeJob.status === 'saving') && (
              <Grid size={{ xs: 12 }}>
                <Box sx={{ mt: 1 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                    <Typography variant="body2" color="text.secondary">
                      {activeJob.status === 'uploading' ? 'Đang tải video lên...' : 'Đang lưu thông tin tập phim...'}
                    </Typography>
                    <Typography variant="body2" color="primary" sx={{ fontWeight: 700 }}>
                      {activeJob.progress}%
                    </Typography>
                  </Box>
                  <LinearProgress
                    variant={activeJob.status === 'uploading' ? 'determinate' : 'indeterminate'}
                    value={activeJob.progress}
                    sx={{ height: 10, borderRadius: 5 }}
                  />
                  <Button onClick={returnToEpisodeManagement} sx={{ mt: 1.5 }}>
                    Tiếp tục dùng chức năng khác (upload vẫn chạy nền)
                  </Button>
                </Box>
              </Grid>
            )}

            <Grid size={{ xs: 12 }}>
              <Button
                fullWidth
                variant="contained"
                size="large"
                type="submit"
                disabled={
                  fieldsDisabled ||
                  readingMetadata ||
                  anotherJobIsBusy ||
                  (!isEdit && (!file || !metadata)) ||
                  (Boolean(file) && !metadata)
                }
                sx={{ py: 1.5, borderRadius: 2, fontWeight: 700 }}
              >
                {savingDetails
                  ? 'Đang lưu...'
                  : activeJob?.status === 'error'
                    ? 'Thử upload lại'
                    : isEdit
                      ? file ? 'Upload và cập nhật tập phim' : 'Lưu thay đổi'
                      : 'Bắt đầu upload'}
              </Button>
            </Grid>
          </Grid>
        </form>
      </Paper>
    </Container>
  );
};

export default EpisodeUpload;
