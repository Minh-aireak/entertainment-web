import React, { memo, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Autocomplete,
  Box,
  Button,
  CircularProgress,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
} from '@mui/material';
import { Add, Delete, Edit } from '@mui/icons-material';
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

  useEffect(() => {
    filmService.getPageFilms(1, 100).then((response) => {
      if (response.code === 1000 && response.result) setFilms(response.result.data);
    }).catch(() => toast.error('Không thể tải danh sách phim'));
  }, []);

  const loadEpisodes = async (filmId: string) => {
    setLoading(true);
    try {
      const response = await filmService.getEpisodesByFilm(filmId);
      if (response.code === 1000 && response.result) {
        setEpisodes(response.result);
      } else {
        toast.error(response.message || 'Không thể tải danh sách tập phim');
      }
    } catch (error: unknown) {
      console.error('Failed to load episodes:', error);
      toast.error('Không thể tải danh sách tập phim');
    } finally {
      setLoading(false);
    }
  };

  const handleSelectFilm = (film: FilmSummaryResponse | null) => {
    setSelectedFilm(film);
    setEpisodes([]);
    if (film) void loadEpisodes(film.id);
  };

  const openEditPage = (episode: EpisodeResponse) => {
    if (!selectedFilm) return;
    navigate(`/film/${selectedFilm.id}/upload-episode`, { state: { episode } });
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
        setEpisodes((current) => current.filter((item) => item.id !== episode.id));
      } else {
        toast.error(response.message || 'Xóa tập phim thất bại');
      }
    } catch (error: unknown) {
      console.error('Failed to delete episode:', error);
      toast.error('Xóa tập phim thất bại');
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
        <Autocomplete
          options={films}
          getOptionLabel={(film) => film.title}
          value={selectedFilm}
          onChange={(_event, value) => handleSelectFilm(value)}
          sx={{ width: 360, maxWidth: '100%' }}
          renderInput={(params) => <TextField {...params} label="Chọn phim để quản lý tập" size="small" />}
        />
        <Button
          variant="contained"
          startIcon={<Add />}
          disabled={!selectedFilm}
          onClick={() => selectedFilm && navigate(
            `/film/${selectedFilm.id}/upload-episode`,
            { state: { newUpload: true } },
          )}
        >
          Thêm tập phim mới
        </Button>
      </Box>

      {selectedFilm && (
        <TableContainer component={Paper} sx={{ borderRadius: 3 }}>
          <Table sx={{ minWidth: 650 }}>
            <TableHead sx={{ bgcolor: 'action.hover' }}>
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
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell>
                </TableRow>
              ) : episodes.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} align="center" sx={{ py: 6 }}>Phim này chưa có tập nào</TableCell>
                </TableRow>
              ) : episodes.map((episode) => (
                <TableRow key={episode.id} hover>
                  <TableCell>{episode.seasonNumber}</TableCell>
                  <TableCell>{episode.episodeNumber}</TableCell>
                  <TableCell sx={{ fontWeight: 'medium' }}>{episode.title}</TableCell>
                  <TableCell>{episode.durationMinutes}</TableCell>
                  <TableCell align="right">
                    <IconButton color="primary" title="Chỉnh sửa và thay video" onClick={() => openEditPage(episode)}>
                      <Edit fontSize="small" />
                    </IconButton>
                    <IconButton color="error" title="Xóa" onClick={() => handleDelete(episode)}>
                      <Delete fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
};

export default memo(EpisodesTab);
