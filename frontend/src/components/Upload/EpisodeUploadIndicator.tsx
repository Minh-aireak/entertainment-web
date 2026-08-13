import React from 'react';
import { Box, CircularProgress, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { CheckCircle, Close, CloudUpload, DragIndicator, Error as ErrorIcon, Save } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useEpisodeUpload } from '../../contexts/episodeUploadContextValue';
import { useDraggableFloating } from '../../hooks/useDraggableFloating';
import { useTranslation } from 'react-i18next';

const EpisodeUploadIndicator: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { job, clearJob } = useEpisodeUpload();
  const { position, dragging, dragHandleProps } = useDraggableFloating('episode-upload-indicator-position', 340, 96);

  if (!job) return null;

  const isUploading = job.status === 'uploading';
  const isSaving = job.status === 'saving';
  const canDismiss = job.status === 'error' || job.status === 'success';
  const statusLabel = isUploading
    ? t('uploadProgress', { progress: job.progress })
    : isSaving
      ? t('savingEpisode')
      : job.status === 'success'
        ? t('uploadComplete')
        : t('uploadError');

  return (
    <Tooltip title={t('reopenUpload')} placement="left">
      <Paper
        role="button"
        tabIndex={0}
        onClick={() => navigate(`/film/${job.filmId}/upload-episode`)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            navigate(`/film/${job.filmId}/upload-episode`);
          }
        }}
        elevation={12}
        sx={{
          position: 'fixed',
          left: position.x,
          top: position.y,
          zIndex: (theme) => theme.zIndex.snackbar,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          width: { xs: 'calc(100vw - 24px)', sm: 340 },
          maxWidth: 340,
          p: 1.5,
          pr: canDismiss ? 5 : 1.5,
          borderRadius: 3,
          border: '1px solid',
          borderColor: job.status === 'error' ? 'error.main' : 'primary.main',
          cursor: 'pointer',
          userSelect: 'none',
          '&:hover': { transform: 'translateY(-2px)', boxShadow: 16 },
          transition: 'transform 150ms ease, box-shadow 150ms ease',
        }}
      >
        <Box
          {...dragHandleProps}
          aria-label={t('moveUploadStatus')}
          title={t('dragToMove')}
          onClick={(event) => event.stopPropagation()}
          sx={{
            alignSelf: 'stretch',
            display: 'grid',
            placeItems: 'center',
            ml: -1,
            cursor: dragging ? 'grabbing' : 'grab',
            touchAction: 'none',
            color: 'text.secondary',
          }}
        >
          <DragIndicator fontSize="small" />
        </Box>
        {canDismiss && (
          <IconButton
            aria-label={job.status === 'error' ? t('cancelFailedUpload') : t('closeUploadNotice')}
            title={job.status === 'error' ? t('cancelAction') : t('close')}
            size="small"
            onClick={(event) => {
              event.stopPropagation();
              clearJob();
            }}
            onKeyDown={(event) => event.stopPropagation()}
            sx={{ position: 'absolute', top: 6, right: 6 }}
          >
            <Close fontSize="small" />
          </IconButton>
        )}
        <Box sx={{ position: 'relative', width: 64, height: 64, flexShrink: 0 }}>
          {(isUploading || isSaving) && (
            <CircularProgress
              variant={isUploading ? 'determinate' : 'indeterminate'}
              value={job.progress}
              size={64}
              thickness={4}
            />
          )}
          <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center' }}>
            {isUploading && <CloudUpload color="primary" sx={{ fontSize: 30 }} />}
            {isSaving && <Save color="primary" sx={{ fontSize: 28 }} />}
            {job.status === 'success' && <CheckCircle color="success" sx={{ fontSize: 58 }} />}
            {job.status === 'error' && <ErrorIcon color="error" sx={{ fontSize: 58 }} />}
          </Box>
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
            {statusLabel}
          </Typography>
          <Typography variant="body2" noWrap color="text.secondary">
            {t('seasonEpisodeLabel', { season: job.seasonNumber, episode: job.episodeNumber })}
          </Typography>
          <Typography variant="caption" noWrap sx={{ display: 'block', maxWidth: 220 }}>
            {job.file.name}
          </Typography>
        </Box>
      </Paper>
    </Tooltip>
  );
};

export default EpisodeUploadIndicator;
