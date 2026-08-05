import React, { useEffect, useState } from 'react';
import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Avatar,
  Paper,
  CircularProgress,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Select,
  InputLabel,
  FormControl,
  OutlinedInput,
  Chip,
  Checkbox,
  FormControlLabel,
  TablePagination,
  IconButton,
  Typography,
} from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import { Add, Delete } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { filmService } from '../../api/filmService';
import type { FilmSummaryResponse, DirectorResponse, ActorResponse, FilmStatus } from '../../models';
import AvatarUploadField from './AvatarUploadField';

const GENRES = ['ACTION', 'COMEDY', 'DRAMA', 'HORROR', 'ROMANCE', 'SCI_FI', 'THRILLER', 'DOCUMENTARY', 'ANIMATION', 'FANTASY'];
const COUNTRIES = ['USA', 'VIETNAM', 'KOREA', 'JAPAN', 'CHINA', 'FRANCE', 'UK', 'GERMANY', 'INDIA', 'THAILAND'];
const STATUSES: FilmStatus[] = ['NOW_PLAYING', 'UPCOMING', 'ENDED', 'ARCHIVED'];

interface CastRow {
  actorId: string;
  characterName: string;
}

const EMPTY_FORM = {
  title: '',
  description: '',
  thumbnailUrl: '',
  trailerUrl: '',
  durationMinutes: 0,
  releaseDate: '',
  series: false,
  directorId: '',
  season: 1,
  country: '',
  genres: [] as string[],
  status: 'UPCOMING' as FilmStatus,
};

const FilmsTab: React.FC = () => {
  const [films, setFilms] = useState<FilmSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalElements, setTotalElements] = useState(0);

  const [directors, setDirectors] = useState<DirectorResponse[]>([]);
  const [actors, setActors] = useState<ActorResponse[]>([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [casts, setCasts] = useState<CastRow[]>([]);
  const [saving, setSaving] = useState(false);

  const loadFilms = async () => {
    setLoading(true);
    try {
      const response = await filmService.getPageFilms(page, rowsPerPage);
      if (response.code === 1000 && response.result) {
        setFilms(response.result.data);
        setTotalElements(response.result.totalElement);
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Không thể tải danh sách phim');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadFilms();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, rowsPerPage]);

  const openCreateDialog = async () => {
    setForm(EMPTY_FORM);
    setCasts([]);
    setDialogOpen(true);
    try {
      const [directorsRes, actorsRes] = await Promise.all([
        filmService.getAllDirectors(1, 100),
        filmService.getAllActors(1, 100),
      ]);
      if (directorsRes.code === 1000 && directorsRes.result) setDirectors(directorsRes.result.data);
      if (actorsRes.code === 1000 && actorsRes.result) setActors(actorsRes.result.data);
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Không thể tải danh sách diễn viên/đạo diễn');
    }
  };

  const handleGenresChange = (event: SelectChangeEvent<string[]>) => {
    const value = event.target.value;
    setForm((prev) => ({ ...prev, genres: typeof value === 'string' ? value.split(',') : value }));
  };

  const addCastRow = () => setCasts((prev) => [...prev, { actorId: '', characterName: '' }]);
  const removeCastRow = (index: number) => setCasts((prev) => prev.filter((_, i) => i !== index));
  const updateCastRow = (index: number, patch: Partial<CastRow>) =>
    setCasts((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));

  const handleSave = async () => {
    if (!form.title.trim() || !form.description.trim() || !form.directorId || !form.country || form.genres.length === 0) {
      toast.error('Vui lòng điền đầy đủ tiêu đề, mô tả, đạo diễn, quốc gia và ít nhất 1 thể loại');
      return;
    }

    setSaving(true);
    try {
      const response = await filmService.createFilm({
        title: form.title,
        description: form.description,
        thumbnailUrl: form.thumbnailUrl,
        trailerUrl: form.trailerUrl,
        durationMinutes: form.durationMinutes,
        releaseDate: form.releaseDate ? new Date(form.releaseDate).toISOString() : new Date().toISOString(),
        series: form.series,
        directorId: form.directorId,
        season: form.season,
        country: form.country,
        genres: form.genres,
        status: form.status,
        casts: casts
          .filter((c) => c.actorId)
          .map((c, index) => ({ actorId: c.actorId, characterName: c.characterName, displayOrder: index })),
      });

      if (response.code === 1000) {
        toast.success('Tạo phim thành công');
        setDialogOpen(false);
        loadFilms();
      } else {
        toast.error(response.message || 'Tạo phim thất bại');
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Tạo phim thất bại');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>
          Thêm phim mới
        </Button>
      </Box>

      <TableContainer component={Paper} sx={{ borderRadius: 3 }}>
        <Table sx={{ minWidth: 650 }}>
          <TableHead sx={{ bgcolor: 'rgba(255, 255, 255, 0.05)' }}>
            <TableRow>
              <TableCell sx={{ fontWeight: 'bold' }}>Ảnh</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>Tiêu đề</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>Trạng thái</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>Số tập</TableCell>
              <TableCell sx={{ fontWeight: 'bold' }}>Đánh giá</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow><TableCell colSpan={5} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell></TableRow>
            ) : films.length === 0 ? (
              <TableRow><TableCell colSpan={5} align="center" sx={{ py: 6 }}>Chưa có phim nào</TableCell></TableRow>
            ) : (
              films.map((film) => (
                <TableRow key={film.id} hover>
                  <TableCell><Avatar variant="rounded" src={film.thumbnailUrl || undefined} /></TableCell>
                  <TableCell sx={{ fontWeight: 'medium' }}>{film.title}</TableCell>
                  <TableCell><Chip size="small" label={film.status || 'UPCOMING'} /></TableCell>
                  <TableCell>{film.episodeCount}</TableCell>
                  <TableCell>{film.averageRating.toFixed(1)} ({film.ratingCount})</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={totalElements}
          page={page - 1}
          onPageChange={(_e, newPage) => setPage(newPage + 1)}
          rowsPerPage={rowsPerPage}
          onRowsPerPageChange={(e) => {
            setRowsPerPage(parseInt(e.target.value, 10));
            setPage(1);
          }}
          labelRowsPerPage="Số dòng/trang"
        />
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>Thêm phim mới</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <TextField label="Tiêu đề" value={form.title} onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))} fullWidth required />
          <TextField label="Mô tả" value={form.description} onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))} fullWidth multiline minRows={2} required />
          <AvatarUploadField label="URL ảnh thumbnail" value={form.thumbnailUrl} onChange={(url) => setForm((p) => ({ ...p, thumbnailUrl: url }))} />

          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
            <TextField
              label="Video trailer"
              value={form.trailerUrl}
              onChange={(e) => setForm((p) => ({ ...p, trailerUrl: e.target.value }))}
              fullWidth
              helperText="Dán URL video trailer đã upload (dùng trang Upload tập phim để lấy URL nếu cần)"
            />
          </Box>

          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="Thời lượng (phút)"
              type="number"
              value={form.durationMinutes}
              onChange={(e) => setForm((p) => ({ ...p, durationMinutes: parseInt(e.target.value, 10) || 0 }))}
              fullWidth
            />
            <TextField
              label="Ngày phát hành"
              type="date"
              value={form.releaseDate}
              onChange={(e) => setForm((p) => ({ ...p, releaseDate: e.target.value }))}
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Mùa (Season)"
              type="number"
              value={form.season}
              onChange={(e) => setForm((p) => ({ ...p, season: parseInt(e.target.value, 10) || 1 }))}
              fullWidth
            />
          </Box>

          <FormControlLabel
            control={<Checkbox checked={form.series} onChange={(e) => setForm((p) => ({ ...p, series: e.target.checked }))} />}
            label="Phim bộ (nhiều tập)"
          />

          <Box sx={{ display: 'flex', gap: 2 }}>
            <FormControl fullWidth required>
              <InputLabel id="director-label">Đạo diễn</InputLabel>
              <Select
                labelId="director-label"
                label="Đạo diễn"
                value={form.directorId}
                onChange={(e) => setForm((p) => ({ ...p, directorId: e.target.value }))}
              >
                {directors.map((d) => (
                  <MenuItem key={d.id} value={d.id}>{d.name}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControl fullWidth required>
              <InputLabel id="country-label">Quốc gia</InputLabel>
              <Select
                labelId="country-label"
                label="Quốc gia"
                value={form.country}
                onChange={(e) => setForm((p) => ({ ...p, country: e.target.value }))}
              >
                {COUNTRIES.map((c) => (
                  <MenuItem key={c} value={c}>{c}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel id="status-label">Trạng thái</InputLabel>
              <Select
                labelId="status-label"
                label="Trạng thái"
                value={form.status}
                onChange={(e) => setForm((p) => ({ ...p, status: e.target.value as FilmStatus }))}
              >
                {STATUSES.map((s) => (
                  <MenuItem key={s} value={s}>{s}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>

          <FormControl fullWidth required>
            <InputLabel id="genres-label">Thể loại</InputLabel>
            <Select
              labelId="genres-label"
              multiple
              value={form.genres}
              onChange={handleGenresChange}
              input={<OutlinedInput label="Thể loại" />}
              renderValue={(selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {(selected as string[]).map((v) => <Chip key={v} label={v} size="small" />)}
                </Box>
              )}
            >
              {GENRES.map((g) => (
                <MenuItem key={g} value={g}>{g}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="subtitle2">Diễn viên tham gia</Typography>
              <Button size="small" startIcon={<Add />} onClick={addCastRow}>Thêm diễn viên</Button>
            </Box>
            {casts.map((row, index) => (
              <Box key={index} sx={{ display: 'flex', gap: 2, mb: 1, alignItems: 'center' }}>
                <FormControl fullWidth size="small">
                  <InputLabel id={`cast-actor-${index}`}>Diễn viên</InputLabel>
                  <Select
                    labelId={`cast-actor-${index}`}
                    label="Diễn viên"
                    value={row.actorId}
                    onChange={(e) => updateCastRow(index, { actorId: e.target.value })}
                  >
                    {actors.map((a) => (
                      <MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <TextField
                  size="small"
                  label="Vai diễn"
                  value={row.characterName}
                  onChange={(e) => updateCastRow(index, { characterName: e.target.value })}
                  fullWidth
                />
                <IconButton color="error" onClick={() => removeCastRow(index)}>
                  <Delete fontSize="small" />
                </IconButton>
              </Box>
            ))}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Hủy</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving}>
            {saving ? 'Đang lưu...' : 'Tạo phim'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default FilmsTab;
