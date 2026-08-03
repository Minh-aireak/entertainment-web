import React, { useState } from 'react';
import {
  Box,
  Typography,
  TextField,
  Button,
  LinearProgress,
  Paper,
  Grid,
  Container,
} from '@mui/material';
import { CloudUpload, Movie } from '@mui/icons-material';
import { useParams, useNavigate } from 'react-router-dom';
import { fileService } from '../../api/fileService';
import { filmService } from '../../api/filmService';
import toast from 'react-hot-toast';

const EpisodeUpload: React.FC = () => {
  const { filmId } = useParams<{ filmId: string }>();
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    seasonNumber: 1,
    episodeNumber: 1,
    title: '',
  });
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === 'title' ? value : parseInt(value) || 0,
    }));
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
    }
  };

  const uploadInChunks = async (file: File): Promise<{ url: string, duration?: number }> => {
    // 1. Init
    const initRes = await fileService.initChunkedUpload();
    const uploadId = initRes.result;

    const chunkSize = 5 * 1024 * 1024; // 5MB
    const totalChunks = Math.ceil(file.size / chunkSize);

    for (let i = 0; i < totalChunks; i++) {
      const start = i * chunkSize;
      const end = Math.min(file.size, start + chunkSize);
      const chunk = file.slice(start, end);

      await fileService.uploadChunk(uploadId, i, chunk);
      
      const currentProgress = Math.round(((i + 1) / totalChunks) * 90);
      setProgress(currentProgress);
    }

    // 2. Complete
    const completeRes = await fileService.completeChunkedUpload(
      uploadId,
      file.name,
      file.type
    );
    
    setProgress(100);
    return {
      url: completeRes.result.url,
      duration: completeRes.result.duration
    };
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file || !filmId) {
      toast.error('Vui lòng chọn file video và đảm bảo ID phim hợp lệ');
      return;
    }

    setUploading(true);
    setProgress(0);

    try {
      // Step 1: Upload Video
      toast.loading('Đang upload video...', { id: 'upload' });
      const uploadResult = await uploadInChunks(file);
      toast.success('Upload video thành công!', { id: 'upload' });

      // Step 2: Create Episode in Film Service
      toast.loading('Đang cập nhật thông tin tập phim...', { id: 'episode' });
      await filmService.createEpisode({
        ...formData,
        videoUrl: uploadResult.url,
        durationMinutes: uploadResult.duration ? Math.floor(uploadResult.duration / 60) : 0,
        filmId,
      });
      toast.success('Thêm tập phim thành công!', { id: 'episode' });

      // Reset or Navigate
      navigate(`/film/${filmId}`);
    } catch (error) {
      console.error('Upload failed:', error);
      toast.error('Có lỗi xảy ra trong quá trình upload', { id: 'upload' });
    } finally {
      setUploading(false);
    }
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Paper elevation={3} sx={{ p: 4, borderRadius: 3, bgcolor: 'rgba(255, 255, 255, 0.05)' }}>
        <Box sx={{ mb: 4, textAlign: 'center' }}>
          <Movie color="primary" sx={{ fontSize: 40, mb: 1 }} />
          <Typography variant="h4" sx={{ fontWeight: 700 }}>
            Upload Tập Phim Mới
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Sử dụng cơ chế upload chunked để đảm bảo tính ổn định cho video dung lượng lớn.
          </Typography>
        </Box>

        <form onSubmit={handleSubmit}>
          <Grid container spacing={3}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                fullWidth
                label="Mùa (Season)"
                name="seasonNumber"
                type="number"
                value={formData.seasonNumber}
                onChange={handleInputChange}
                required
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                fullWidth
                label="Tập số (Episode)"
                name="episodeNumber"
                type="number"
                value={formData.episodeNumber}
                onChange={handleInputChange}
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
                required
              />
            </Grid>

            <Grid size={{ xs: 12 }}>
              <Box
                sx={{
                  border: '2px dashed rgba(255, 255, 255, 0.2)',
                  borderRadius: 2,
                  p: 4,
                  textAlign: 'center',
                  cursor: 'pointer',
                  '&:hover': { borderColor: 'primary.main', bgcolor: 'rgba(255, 255, 255, 0.02)' },
                }}
                component="label"
              >
                <input type="file" hidden accept="video/*" onChange={handleFileChange} />
                <CloudUpload sx={{ fontSize: 48, color: 'text.secondary', mb: 1 }} />
                <Typography variant="h6">
                  {file ? file.name : 'Nhấn để chọn video hoặc kéo thả vào đây'}
                </Typography>
                {file && (
                  <Typography variant="caption" color="text.secondary">
                    Size: {(file.size / (1024 * 1024)).toFixed(2)} MB
                  </Typography>
                )}
              </Box>
            </Grid>

            {uploading && (
              <Grid size={{ xs: 12 }}>  
                <Box sx={{ width: '100%', mt: 2 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                    <Typography variant="body2" color="text.secondary">Đang tải lên...</Typography>
                    <Typography variant="body2" color="primary" sx={{ fontWeight: 700 }}>{progress}%</Typography>
                  </Box>
                  <LinearProgress variant="determinate" value={progress} sx={{ height: 10, borderRadius: 5 }} />
                </Box>
              </Grid>
            )}

            <Grid size={{ xs: 12 }}>
              <Button
                fullWidth
                variant="contained"
                size="large"
                type="submit"
                disabled={uploading || !file}
                sx={{ py: 1.5, borderRadius: 2, fontWeight: 700 }}
              >
                {uploading ? 'Đang xử lý...' : 'Bắt đầu Upload'}
              </Button>
            </Grid>
          </Grid>
        </form>
      </Paper>
    </Container>
  );
};

export default EpisodeUpload;
