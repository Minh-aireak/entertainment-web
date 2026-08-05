import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  IconButton,
  Paper,
  CircularProgress,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Autocomplete,
} from '@mui/material';
import { Add, Edit, Delete } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { filmService } from '../../api/filmService';
import type { EpisodeResponse, FilmSummaryResponse } from '../../models';
import { useConfirmDialog } from '../../contexts/ConfirmDialogContext';

const EpisodesTab: React.FC = () => {
  const navigate = useNavigate();
  const confirm = useConfirmDialog();

  const [films, setFilms] = useState<FilmSummaryResponse[]>([]);
  const [selectedFilm, setSelectedFilm] = useState<FilmSummaryResponse | null>(null);
  const [episodes, setEpisodes] = useState<EpisodeResponse[]>([]);
  const [loading, setLoading] = useState(false);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingEpisode, setEditingEpisode] = useState<EpisodeResponse | null>(null);
  const [form, setForm] = useState({ seasonNumber: 1, episodeNumber: 1, title: '', durationMinutes: 0 });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    filmService.getPageFilms(1, 100).then((response) => {
      if (response.code === 1000 && response.result) setFilms(response.result.data);
    }).catch(() => toast.error('Không thể tải danh sách phim'));
  }, []);

  const loadEpisodes = async (filmId: string) => {
    setLoading(true);
    try {
      const response = await filmService.getEpisodesByFilm(filmId);
      if (response.code === 1000 && response.result) setEpisodes(response.result);
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Không thể tải danh sách tập phim');
    } finally {
      setLoading(false);
    }
  };

  const handleSelectFilm = (film: FilmSummaryResponse | null) => {
    setSelectedFilm(film);
    setEpisodes([]);
    if (film) loadEpisodes(film.id);
  };

  const openEditDialog = (episode: EpisodeResponse) => {
    setEditingEpisode(episode);
    setForm({
      seasonNumber: episode.seasonNumber,
      episodeNumber: episode.episodeNumber,
      title: episode.title,
      durationMinutes: episode.durationMinutes,
    });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!editingEpisode || !selectedFilm) return;
    setSaving(true);
    try {
      const response = await filmService.updateEpisode(editingEpisode.id, {
        ...form,
        videoUrl: editingEpisode.videoUrl,
        filmId: selectedFilm.id,
      });
      if (response.code === 1000) {
        toast.success('Cập nhật tập phim thành công');
        setDialogOpen(false);
        loadEpisodes(selectedFilm.id);
      } else {
        toast.error(response.message || 'Cập nhật thất bại');
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Cập nhật thất bại');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (episode: EpisodeResponse) => {
    const confirmed = await confirm({
      title: 'Xóa tập phim',
      message: `Bạn có chắc muốn xóa "${episode.title}" (Mùa ${episode.seasonNumber} - Tập ${episode.episodeNumber})?`,
    });
    if (!confirmed || !selectedFilm) return;

    try {
      const response = await filmService.deleteEpisode(episode.id);
      if (response.code === 1000) {
        toast.success('Xóa tập phim thành công');
        setEpisodes((prev) => prev.filter((e) => e.id !== episode.id));
      } else {
        toast.error(response.message || 'Xóa thất bại');
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Xóa thất bại');
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mb: 2 }}>
        <Autocomplete
          options={films}
          getOptionLabel={(film) => film.title}
          value={selectedFilm}
          onChange={(_e, value) => handleSelectFilm(value)}
          sx={{ width: 360 }}
          renderInput={(params) => <TextField {...params} label="Chọn phim để quản lý tập" size="small" />}
        />
        <Button
          variant="contained"
          startIcon={<Add />}
          disabled={!selectedFilm}
          onClick={() => selectedFilm && navigate(`/film/${selectedFilm.id}/upload-episode`)}
        >
          Thêm tập phim mới
        </Button>
      </Box>

      {selectedFilm && (
        <TableContainer component={Paper} sx={{ borderRadius: 3 }}>
          <Table sx={{ minWidth: 650 }}>
            <TableHead sx={{ bgcolor: 'rgba(255, 255, 255, 0.05)' }}>
              <TableRow>
                <TableCell sx={{ fontWeight: 'bold' }}>Mùa</TableCell>
                <TableCell sx={{ fontWeight: 'bold' }}>Tập</TableCell>
                <TableCell sx={{ fontWeight: 'bold' }}>Tiêu đề</TableCell>
                <TableCell sx={{ fontWeight: 'bold' }}>Thời lượng (phút)</TableCell>
                <TableCell sx={{ fontWeight: 'bold', textAlign: 'right' }}>Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={5} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell></TableRow>
              ) : episodes.length === 0 ? (
                <TableRow><TableCell colSpan={5} align="center" sx={{ py: 6 }}>Phim này chưa có tập nào</TableCell></TableRow>
              ) : (
                episodes.map((episode) => (
                  <TableRow key={episode.id} hover>
                    <TableCell>{episode.seasonNumber}</TableCell>
                    <TableCell>{episode.episodeNumber}</TableCell>
                    <TableCell sx={{ fontWeight: 'medium' }}>{episode.title}</TableCell>
                    <TableCell>{episode.durationMinutes}</TableCell>
                    <TableCell align="right">
                      <IconButton color="primary" title="Chỉnh sửa" onClick={() => openEditDialog(episode)}>
                        <Edit fontSize="small" />
                      </IconButton>
                      <IconButton color="error" title="Xóa" onClick={() => handleDelete(episode)}>
                        <Delete fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Chỉnh sửa tập phim</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <TextField
            label="Tiêu đề"
            value={form.title}
            onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))}
            fullWidth
          />
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="Mùa"
              type="number"
              value={form.seasonNumber}
              onChange={(e) => setForm((p) => ({ ...p, seasonNumber: parseInt(e.target.value, 10) || 1 }))}
              fullWidth
            />
            <TextField
              label="Tập số"
              type="number"
              value={form.episodeNumber}
              onChange={(e) => setForm((p) => ({ ...p, episodeNumber: parseInt(e.target.value, 10) || 1 }))}
              fullWidth
            />
          </Box>
          <TextField
            label="Thời lượng (phút)"
            type="number"
            value={form.durationMinutes}
            onChange={(e) => setForm((p) => ({ ...p, durationMinutes: parseInt(e.target.value, 10) || 0 }))}
            fullWidth
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Hủy</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? 'Đang lưu...' : 'Lưu'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default EpisodesTab;
