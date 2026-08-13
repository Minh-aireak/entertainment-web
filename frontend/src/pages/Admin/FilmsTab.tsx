import React, { memo, useEffect, useMemo, useState } from 'react';
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
  IconButton,
  Typography,
  InputAdornment,
} from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import { Add, Delete, Edit, Search, Clear } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { filmService } from '../../api/filmService';
import type { FilmSummaryResponse, DirectorResponse, ActorResponse, FilmStatus, Genre, Country } from '../../models';
import { GENRE_VALUES, COUNTRY_VALUES } from '../../constants/film';
import { extractYouTubeVideoId } from '../../utils/youtube';
import AvatarUploadField from './AvatarUploadField';
import { useTranslation } from 'react-i18next';
import {
  AdminPagination,
  Pill,
  adminHeaderCellSx,
  adminInputSx,
  adminTableContainerSx,
  adminToolbarSx,
} from './adminUiKit';

const STATUSES: FilmStatus[] = ['ONGOING', 'COMPLETED'];
const STATUS_KEY: Record<FilmStatus, string> = {
  ONGOING: 'filmStatus.ongoing',
  COMPLETED: 'filmStatus.completed',
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
  const { t } = useTranslation();
  const [films, setFilms] = useState<FilmSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const rowsPerPage = 10;
  const [totalElements, setTotalElements] = useState(0);

  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState('');
  const isSearchMode = debouncedSearchTerm.length > 0;
  const [genreFilter, setGenreFilter] = useState<Genre | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<FilmStatus | 'ALL'>('ALL');
  const filtersActive = genreFilter !== 'ALL' || statusFilter !== 'ALL';

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
      toast.error(error.response?.data?.message || t('filmsLoadFailed'));
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
      toast.error(error.response?.data?.message || t('filmSearchFailed'));
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
      toast.error(error.response?.data?.message || t('peopleLoadFailed'));
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
        toast.error(response.message || t('filmDetailsLoadFailed'));
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || t('filmDetailsLoadFailed'));
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
      toast.error(t('filmRequiredFields'));
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
        toast.success(editingId ? t('filmUpdated') : t('filmCreated'));
        setDialogOpen(false);
        loadFilms();
      } else {
        toast.error(response.message || (editingId ? t('filmUpdateFailed') : t('filmCreateFailed')));
      }
    } catch (error: any) {
      toast.error(error.response?.data?.message || (editingId ? t('filmUpdateFailed') : t('filmCreateFailed')));
    } finally {
      setSaving(false);
    }
  };

  const filteredFilms = useMemo(() => films.filter((film) => {
    if (genreFilter !== 'ALL' && !(film.genres || []).includes(genreFilter)) return false;
    if (statusFilter !== 'ALL' && (film.status || 'ONGOING') !== statusFilter) return false;
    return true;
  }), [films, genreFilter, statusFilter]);

  return (
    <Box>
      <Box sx={adminToolbarSx}>
        <TextField
          size="small"
          placeholder={t('searchFilmsByTitle')}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          sx={{ width: 280, ...adminInputSx }}
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
        <FormControl size="small" sx={{ minWidth: 170, ...adminInputSx }}>
          <InputLabel id="genre-filter-label">{t('filterByGenre')}</InputLabel>
          <Select labelId="genre-filter-label" label={t('filterByGenre')} value={genreFilter} onChange={(e) => setGenreFilter(e.target.value as Genre | 'ALL')}>
            <MenuItem value="ALL">{t('allGenres')}</MenuItem>
            {GENRE_VALUES.map((g) => (
              <MenuItem key={g} value={g}>{t(`genre.${g}`)}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 170, ...adminInputSx }}>
          <InputLabel id="film-status-filter-label">{t('filterByStatus')}</InputLabel>
          <Select labelId="film-status-filter-label" label={t('filterByStatus')} value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as FilmStatus | 'ALL')}>
            <MenuItem value="ALL">{t('allStatuses')}</MenuItem>
            {STATUSES.map((s) => (
              <MenuItem key={s} value={s}>{t(STATUS_KEY[s])}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <Box sx={{ flexGrow: 1 }} />
        <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog} sx={{ borderRadius: '10px' }}>
          {t('addNewFilm')}
        </Button>
      </Box>

      <TableContainer component={Paper} sx={adminTableContainerSx}>
        <Table sx={{ minWidth: 650 }}>
          <TableHead>
            <TableRow>
              <TableCell sx={adminHeaderCellSx}>{t('films')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('filterByGenre')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('status')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('episodeNumber')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('rating')}</TableCell>
              <TableCell sx={{ ...adminHeaderCellSx, textAlign: 'right' }}>{t('actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow><TableCell colSpan={6} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell></TableRow>
            ) : filteredFilms.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                  {isSearchMode ? t('noMatchingAdminFilms') : t('noFilms')}
                </TableCell>
              </TableRow>
            ) : (
              filteredFilms.map((film) => (
                <TableRow key={film.id} hover>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Avatar variant="rounded" src={film.thumbnailUrl || undefined} sx={{ width: 44, height: 44 }} />
                      <Box>
                        <Box sx={{ fontWeight: 600 }}>{film.title}</Box>
                        <Box sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>
                          {film.series ? t('seriesFilms') : t('standaloneFilm')}
                        </Box>
                      </Box>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', maxWidth: 220 }}>
                      {(film.genres || []).slice(0, 2).map((g) => (
                        <Chip key={g} size="small" variant="outlined" label={t(`genre.${g}`)} />
                      ))}
                      {(film.genres || []).length > 2 && (
                        <Chip size="small" variant="outlined" label={`+${(film.genres || []).length - 2}`} />
                      )}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Pill
                      label={t(film.status ? STATUS_KEY[film.status] : 'filmStatus.ongoing')}
                      tone={film.status === 'ONGOING' ? 'success' : 'default'}
                      dot
                    />
                  </TableCell>
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
        {!isSearchMode && !filtersActive && (
          <AdminPagination page={page} rowsPerPage={rowsPerPage} totalElements={totalElements} onPageChange={setPage} itemLabel={t('films').toLowerCase()} />
        )}
        {(isSearchMode || filtersActive) && (
          <Box sx={{ px: 2.5, py: 2, borderTop: '1px solid', borderColor: 'divider', fontSize: '0.85rem', color: 'text.secondary' }}>
            {t('showingFilms', { count: filteredFilms.length })}
          </Box>
        )}
      </TableContainer>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>{editingId ? t('editFilm') : t('addNewFilm')}</DialogTitle>
        {loadingFilmDetail ? (
          <DialogContent sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
            <CircularProgress size={28} />
          </DialogContent>
        ) : (
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label={t('title')}
            value={form.title}
            onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))}
            fullWidth
            required
            sx={{ mt: 1 }}
          />
          <TextField label={t('description')} value={form.description} onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))} fullWidth multiline minRows={2} required />
          <AvatarUploadField
            label={t('thumbnailImage')}
            value={form.thumbnailUrl}
            onChange={(url) => setForm((p) => ({ ...p, thumbnailUrl: url }))}
            onFileIdChange={setThumbnailFileId}
            allowManualUrl={false}
          />

          <TextField
            label={t('youtubeTrailerLink')}
            value={form.trailerUrl}
            onChange={(e) => setForm((p) => ({ ...p, trailerUrl: e.target.value }))}
            fullWidth
            placeholder="https://www.youtube.com/watch?v=..."
            helperText={
              form.trailerUrl && !extractYouTubeVideoId(form.trailerUrl)
                ? t('invalidYoutubeLink')
                : t('youtubeTrailerHint')
            }
            error={Boolean(form.trailerUrl) && !extractYouTubeVideoId(form.trailerUrl)}
          />

          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label={t('durationMinutes')}
              type="number"
              value={form.durationMinutes}
              onChange={(e) => setForm((p) => ({ ...p, durationMinutes: parseInt(e.target.value, 10) || 0 }))}
              fullWidth
            />
            <TextField
              label={t('releaseDate')}
              type="date"
              value={form.releaseDate}
              onChange={(e) => setForm((p) => ({ ...p, releaseDate: e.target.value }))}
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label={t('seasonField')}
              type="number"
              value={form.season}
              onChange={(e) => setForm((p) => ({ ...p, season: parseInt(e.target.value, 10) || 1 }))}
              fullWidth
            />
          </Box>

          <FormControlLabel
            control={<Checkbox checked={form.series} onChange={(e) => setForm((p) => ({ ...p, series: e.target.checked }))} />}
            label={t('multiEpisodeFilm')}
          />

          <FormControl fullWidth required>
            <InputLabel id="director-label">{t('director')}</InputLabel>
            <Select
              labelId="director-label"
              multiple
              label={t('director')}
              value={form.directorIds}
              onChange={handleDirectorsChange}
              input={<OutlinedInput label={t('director')} />}
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
              <InputLabel id="country-label">{t('countryLabel')}</InputLabel>
              <Select
                labelId="country-label"
                label={t('countryLabel')}
                value={form.country}
                onChange={(e) => setForm((p) => ({ ...p, country: e.target.value as Country }))}
              >
                {COUNTRY_VALUES.map((c) => (
                  <MenuItem key={c} value={c}>{t(`country.${c}`)}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel id="status-label">{t('status')}</InputLabel>
              <Select
                labelId="status-label"
                label={t('status')}
                value={form.status}
                onChange={(e) => setForm((p) => ({ ...p, status: e.target.value as FilmStatus }))}
              >
                {STATUSES.map((s) => (
                  <MenuItem key={s} value={s}>{t(STATUS_KEY[s])}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>

          <FormControl fullWidth required>
            <InputLabel id="genres-label">{t('genreLabel')}</InputLabel>
            <Select
              labelId="genres-label"
              multiple
              value={form.genres}
              onChange={handleGenresChange}
              input={<OutlinedInput label={t('genreLabel')} />}
              renderValue={(selected) => (
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {(selected as Genre[]).map((v) => <Chip key={v} label={t(`genre.${v}`)} size="small" />)}
                </Box>
              )}
            >
              {GENRE_VALUES.map((g) => (
                <MenuItem key={g} value={g}>{t(`genre.${g}`)}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="subtitle2">{t('participatingActors')}</Typography>
              <Button size="small" startIcon={<Add />} onClick={addCastRow}>{t('addActorToCast')}</Button>
            </Box>
            {casts.map((row, index) => (
              <Box key={index} sx={{ display: 'flex', gap: 2, mb: 1, alignItems: 'center' }}>
                <FormControl fullWidth size="small">
                  <InputLabel id={`cast-actor-${index}`}>{t('actors')}</InputLabel>
                  <Select
                    labelId={`cast-actor-${index}`}
                    label={t('actors')}
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
                  label={t('characterRole')}
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
          <Button onClick={() => setDialogOpen(false)}>{t('cancel')}</Button>
          <Button variant="contained" onClick={handleSave} disabled={saving || loadingFilmDetail}>
            {saving ? t('saving') : editingId ? t('saveChanges') : t('createFilm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default memo(FilmsTab);
