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
import { useTranslation } from 'react-i18next';

interface EpisodeUploadLocationState {
  episode?: EpisodeResponse;
  newUpload?: boolean;
}

const EpisodeUpload: React.FC = () => {
  const { filmId } = useParams<{ filmId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
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
    } catch {
      if (metadataRequestRef.current !== requestId) return;
      setFile(null);
      toast.error(t('videoMetadataReadFailed'));
    } finally {
      if (metadataRequestRef.current === requestId) setReadingMetadata(false);
    }
  };

  const returnToEpisodeManagement = () => navigate('/admin?tab=episodes');

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();

    if (!filmId) {
      toast.error(t('invalidFilmId'));
      return;
    }
    if (anotherJobIsBusy || jobIsBusy) {
      toast.error(t('uploadInProgress'));
      return;
    }

    if (isEdit && !file) {
      if (!editingEpisode || !episodeId) {
        toast.error(t('editingEpisodeMissing'));
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
        if (response.code !== 1000) throw new Error(response.message || t('episodeSaveFailed'));
        toast.success(t('episodeUpdated'));
        returnToEpisodeManagement();
      } catch (error: unknown) {
        toast.error(error instanceof Error ? error.message : t('episodeSaveFailed'));
      } finally {
        setSavingDetails(false);
      }
      return;
    }

    if (!file || !metadata) {
      toast.error(t('validVideoRequired'));
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
        {t('backToEpisodeManagement')}
      </Button>

      <Paper elevation={3} sx={{ p: { xs: 2.5, sm: 4 }, borderRadius: 3 }}>
        <Box sx={{ mb: 4, textAlign: 'center' }}>
          <Movie color="primary" sx={{ fontSize: 44, mb: 1 }} />
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            {isEdit ? t('editEpisode') : t('uploadNewEpisode')}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {t('durationAutoDescription')}
          </Typography>
        </Box>

        {anotherJobIsBusy && (
          <Alert severity="warning" sx={{ mb: 3 }}>
            {t('uploadContinuesDescription')}
          </Alert>
        )}
        {activeJob?.status === 'success' && (
          <Alert
            severity="success"
            sx={{ mb: 3 }}
            action={<Button color="inherit" onClick={handleFinish}>{t('finish')}</Button>}
          >
            {isEdit ? t('episodeEditComplete') : t('episodeCreateComplete')}
          </Alert>
        )}
        {activeJob?.status === 'error' && (
          <Alert
            severity="error"
            sx={{ mb: 3 }}
            action={<Button color="inherit" onClick={handleCancelFailedUpload}>{t('cancelAction')}</Button>}
          >
            {activeJob.error || t('failedUploadRetained')}
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                fullWidth
                label={t('seasonNumber')}
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
                label={t('episodeNumberField')}
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
                label={t('episodeTitle')}
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
                  {file ? file.name : isEdit ? t('chooseReplacementVideo') : t('chooseVideoFile')}
                </Typography>
                {file && (
                  <Typography variant="caption" color="text.secondary">
                    {(file.size / (1024 * 1024)).toFixed(2)} MB
                  </Typography>
                )}
                {metadata && (
                  <Box sx={{ mt: 2, display: 'flex', gap: 1, justifyContent: 'center', flexWrap: 'wrap' }}>
                    <Chip color="success" label={t('fileDuration', { duration: metadata.formattedDuration })} />
                    <Chip variant="outlined" label={t('savedDuration', { minutes: metadata.durationMinutes })} />
                  </Box>
                )}
                {!file && isEdit && editingEpisode && (
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    {t('keepCurrentVideo', { minutes: editingEpisode.durationMinutes })}
                  </Typography>
                )}
              </Box>
            </Grid>

            {activeJob && (activeJob.status === 'uploading' || activeJob.status === 'saving') && (
              <Grid size={{ xs: 12 }}>
                <Box sx={{ mt: 1 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                    <Typography variant="body2" color="text.secondary">
                      {activeJob.status === 'uploading' ? t('uploadingVideo') : t('savingEpisodeDetails')}
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
                    {t('continueInBackground')}
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
                  ? t('saving')
                  : activeJob?.status === 'error'
                    ? t('retryUpload')
                    : isEdit
                      ? file ? t('uploadAndUpdateEpisode') : t('saveChanges')
                      : t('startUpload')}
              </Button>
            </Grid>
          </Grid>
        </form>
      </Paper>
    </Container>
  );
};

export default EpisodeUpload;
