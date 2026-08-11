import React, { memo, useEffect, useState } from 'react';
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
  InputAdornment,
} from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import { Add, Delete, Edit, Search, Clear } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { filmService } from '../../api/filmService';
import type { FilmSummaryResponse, DirectorResponse, ActorResponse, FilmStatus, Genre, Country } from '../../models';
import { GENRE_VALUES, COUNTRY_VALUES, GENRE_LABELS_VI, COUNTRY_LABELS_VI } from '../../constants/film';
import { extractYouTubeVideoId } from '../../utils/youtube';
import AvatarUploadField from './AvatarUploadField';

const STATUSES: FilmStatus[] = ['ONGOING', 'COMPLETED'];
const STATUS_LABEL: Record<FilmStatus, string> = {
  ONGOING: 'Đang cập nhật',
  COMPLETED: 'Hoàn thành',
};

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
  directorIds: [] as string[],
  season: 1,
  country: '' as Country | '',
  genres: [] as Genre[],
  status: 'ONGOING' as FilmStatus,
};

interface FilmsTabProps {
  active?: boolean;
}

const FilmsTab: React.FC<FilmsTabProps> = ({ active = true }) => {
  const [films, setFilms] = useState<FilmSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalElements, setTotalElements] = useState(0);

  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('');
  const isSearchMode = debouncedSearchTerm.length > 0;

  const [directors, setDirectors] = useState<DirectorResponse[]>([]);
  const [actors, setActors] = useState<ActorResponse[]>([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [thumbnailFileId, setThumbnailFileId] = useState('');
  const [casts, setCasts] = useState<CastRow[]>([]);
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [loadingFilmDetail, setLoadingFilmDetail] = useState(false);

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

  const searchFilmsByTitle = async (title: string) => {
    setLoading(true);
    try {
      const response = await filmService.searchFilms(title);
      if (response.code === 1000 && response.result) {
        setFilms(response.result);
        setTotalElements(response.result.length);
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Không thể tìm kiếm phim');
    } finally {
      setLoading(false);
    }
  };

  // Debounce ô search 400ms để tránh gọi API liên tục khi người dùng đang gõ
  useEffect(() => {
    const handle = setTimeout(() => setDebouncedSearchTerm(searchTerm.trim()), 400);
    return () => clearTimeout(handle);
  }, [searchTerm]);

  useEffect(() => {
    if (!active) return;

    if (isSearchMode) {
      searchFilmsByTitle(debouncedSearchTerm);
    } else {
      loadFilms();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, rowsPerPage, debouncedSearchTerm, active]);

  const loadDirectorsAndActors = async () => {
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

  const openCreateDialog = async () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setThumbnailFileId('');
    setCasts([]);
    setDialogOpen(true);
    await loadDirectorsAndActors();
  };

  const openEditDialog = async (film: FilmSummaryResponse) => {
    setEditingId(film.id);
    setDialogOpen(true);
    setLoadingFilmDetail(true);
    try {
      await loadDirectorsAndActors();
      const response = await filmService.getFilmAggregate(film.id);
      if (response.code === 1000 && response.result) {
        const detail = response.result.film;
        setForm({
          title: detail.title,
          description: detail.description,
          thumbnailUrl: detail.thumbnailUrl || '',
          trailerUrl: detail.trailerUrl || '',
          durationMinutes: detail.durationMinutes,
          releaseDate: detail.releaseDate ? new Date(detail.releaseDate).toISOString().slice(0, 10) : '',
          series: detail.series,
          directorIds: (detail.directors || [])
            .slice()
            .sort((a, b) => a.displayOrder - b.displayOrder)
            .map((d) => d.director.id),
          season: detail.season,
          country: detail.country,
          genres: detail.genres || [],
          status: detail.status || 'ONGOING',
        });
        setThumbnailFileId(detail.thumbnailFileId || '');
        setCasts(
          (detail.casts || [])
            .slice()
            .sort((a, b) => a.displayOrder - b.displayOrder)
            .map((c) => ({ actorId: c.actor.id, characterName: c.characterName }))
        );
      } else {
        toast.error(response.message || 'Không thể tải thông tin phim');
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Không thể tải thông tin phim');
    } finally {
      setLoadingFilmDetail(false);
    }
  };

  const handleGenresChange = (event: SelectChangeEvent<Genre[]>) => {
    const value = event.target.value;
    setForm((prev) => ({ ...prev, genres: typeof value === 'string' ? (value.split(',') as Genre[]) : value }));
  };

  const handleDirectorsChange = (event: SelectChangeEvent<string[]>) => {
    const value = event.target.value;
    setForm((prev) => ({ ...prev, directorIds: typeof value === 'string' ? value.split(',') : value }));
  };

  const addCastRow = () => setCasts((prev) => [...prev, { actorId: '', characterName: '' }]);
  const removeCastRow = (index: number) => setCasts((prev) => prev.filter((_, i) => i !== index));
  const updateCastRow = (index: number, patch: Partial<CastRow>) =>
    setCasts((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));

  const handleSave = async () => {
    if (!form.title.trim() || !form.description.trim() || form.directorIds.length === 0 || !form.country || form.genres.length === 0) {
      toast.error('Vui lòng điền đầy đủ tiêu đề, mô tả, đạo diễn, quốc gia và ít nhất 1 thể loại');
      return;
    }

    setSaving(true);
    try {
      const request = {
        title: form.title,
        description: form.description,
        thumbnailUrl: form.thumbnailUrl,
        thumbnailFileId,
        trailerUrl: form.trailerUrl,
        durationMinutes: form.durationMinutes,
        releaseDate: form.releaseDate ? new Date(form.releaseDate).toISOString() : new Date().toISOString(),
        series: form.series,
        directorIds: form.directorIds,
        season: form.season,
        country: form.country,
        genres: form.genres,
        status: form.status,
        casts: casts
          .filter((c) => c.actorId)
          .map((c, index) => ({ actorId: c.actorId, characterName: c.characterName, displayOrder: index })),
      };

      const response = editingId
        ? await filmService.updateFilm(editingId, request)
        : await filmService.createFilm(request);

      if (response.code === 1000) {
        toast.success(editingId ? 'Cập nhật phim thành công' : 'Tạo phim thành công');
        setDialogOpen(false);
        loadFilms();
      } else {
        toast.error(response.message || (editingId ? 'Cập nhật phim thất bại' : 'Tạo phim thất bại'));
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || (editingId ? 'Cập nhật phim thất bại' : 'Tạo phim thất bại'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, mb: 2 }}>
        <TextField
          size="small"
          placeholder="Tìm kiếm phim theo tên..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          sx={{ width: 320 }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <Search fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: searchTerm ? (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => setSearchTerm('')}>
                    <Clear fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : undefined,
            },
          }}
        />
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
              <TableCell sx={{ fontWeight: 'bold' }} align="right">Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow><TableCell colSpan={6} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell></TableRow>
            ) : films.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                  {isSearchMode ? 'Không tìm thấy phim nào phù hợp' : 'Chưa có phim nào'}
                </TableCell>
              </TableRow>
            ) : (
              films.map((film) => (
                <TableRow key={film.id} hover>
                  <TableCell><Avatar variant="rounded" src={film.thumbnailUrl || undefined} /></TableCell>
                  <TableCell sx={{ fontWeight: 'medium' }}>{film.title}</TableCell>
                  <TableCell><Chip size="small" label={film.status ? STATUS_LABEL[film.status] : 'Đang cập nhật'} color={film.status === 'ONGOING' ? 'success' : 'default'} /></TableCell>
                  <TableCell>{film.episodeCount}</TableCell>
                  <TableCell>{film.averageRating.toFixed(1)} ({film.ratingCount})</TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={() => openEditDialog(film)}>
                      <Edit fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        {!isSearchMode && (
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
        )}
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>{editingId ? 'Chỉnh sửa phim' : 'Thêm phim mới'}</DialogTitle>
        {loadingFilmDetail ? (
          <DialogContent sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
            <CircularProgress size={28} />
          </DialogContent>
        ) : (
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label="Tiêu đề"
            value={form.title}
            onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))}
            fullWidth
            required
            sx={{ mt: 1 }}
          />
          <TextField label="Mô tả" value={form.description} onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))} fullWidth multiline minRows={2} required />
          <AvatarUploadField
            label="Ảnh thumbnail"
            value={form.thumbnailUrl}
            onChange={(url) => setForm((p) => ({ ...p, thumbnailUrl: url }))}
            onFileIdChange={setThumbnailFileId}
            allowManualUrl={false}
          />

          <TextField
            label="Link trailer YouTube"
            value={form.trailerUrl}
            onChange={(e) => setForm((p) => ({ ...p, trailerUrl: e.target.value }))}
            fullWidth
            placeholder="https://www.youtube.com/watch?v=..."
            helperText={
              form.trailerUrl && !extractYouTubeVideoId(form.trailerUrl)
                ? 'Không nhận diện được link YouTube - kiểm tra lại URL'
                : 'Dán link video YouTube (watch?v=..., youtu.be/..., hoặc embed/...) làm trailer cho phim'
            }
            error={Boolean(form.trailerUrl) && !extractYouTubeVideoId(form.trailerUrl)}
          />

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

          <FormControl fullWidth required>
            <InputLabel id="director-label">Đạo diễn</InputLabel>
            <Select
              labelId="director-label"
              multiple
              label="Đạo diễn"
              value={form.directorIds}
              onChange={handleDirectorsChange}
              input={<OutlinedInput label="Đạo diễn" />}
              renderValue={(selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {(selected as string[]).map((id) => (
                    <Chip key={id} label={directors.find((d) => d.id === id)?.name || id} size="small" />
                  ))}
                </Box>
              )}
            >
              {directors.map((d) => (
                <MenuItem key={d.id} value={d.id}>{d.name}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <Box sx={{ display: 'flex', gap: 2 }}>
            <FormControl fullWidth required>
              <InputLabel id="country-label">Quốc gia</InputLabel>
              <Select
                labelId="country-label"
                label="Quốc gia"
                value={form.country}
                onChange={(e) => setForm((p) => ({ ...p, country: e.target.value as Country }))}
              >
                {COUNTRY_VALUES.map((c) => (
                  <MenuItem key={c} value={c}>{COUNTRY_LABELS_VI[c]}</MenuItem>
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
                  <MenuItem key={s} value={s}>{STATUS_LABEL[s]}</MenuItem>
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
                  {(selected as Genre[]).map((v) => <Chip key={v} label={GENRE_LABELS_VI[v]} size="small" />)}
                </Box>
              )}
            >
              {GENRE_VALUES.map((g) => (
                <MenuItem key={g} value={g}>{GENRE_LABELS_VI[g]}</MenuItem>
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
        )}
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Hủy</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving || loadingFilmDetail}>
            {saving ? 'Đang lưu...' : editingId ? 'Lưu thay đổi' : 'Tạo phim'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default memo(FilmsTab);
