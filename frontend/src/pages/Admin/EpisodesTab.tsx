import React, { memo, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Autocomplete,
  Avatar,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
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
import type { EpisodeSummaryResponse, FilmCategory, FilmSummaryResponse } from '../../models';
import { useConfirmDialog } from '../../contexts/ConfirmDialogContext';
import { useTranslation } from 'react-i18next';
import {
  AdminPagination,
  Pill,
  adminHeaderCellSx,
  adminInputSx,
  adminTableContainerSx,
  adminToolbarSx,
} from './adminUiKit';

const ROWS_PER_PAGE = 10;

const EpisodesTab: React.FC = () => {
  const navigate = useNavigate();
  const confirm = useConfirmDialog();
  const { t } = useTranslation();

  // Unfiltered - always holds every film, so episode row thumbnails/titles resolve
  // correctly no matter which film category is currently narrowing the pickers below.
  const [allFilms, setAllFilms] = useState<FilmSummaryResponse[]>([]);
  // Options for the film pickers (dropdown + "pick a film" dialog), narrowed by categoryFilter.
  const [categoryFilms, setCategoryFilms] = useState<FilmSummaryResponse[]>([]);
  const [episodes, setEpisodes] = useState<EpisodeSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const [categoryFilter, setCategoryFilter] = useState<'ALL' | FilmCategory>('ALL');
  const [filmFilter, setFilmFilter] = useState<'ALL' | string>('ALL');
  const [videoFilter, setVideoFilter] = useState<'ALL' | 'PUBLISHED' | 'MISSING'>('ALL');
  const videoFilterActive = videoFilter !== 'ALL';

  const [pickFilmDialogOpen, setPickFilmDialogOpen] = useState(false);
  const [pickedFilm, setPickedFilm] = useState<FilmSummaryResponse | null>(null);

  useEffect(() => {
    filmService.getPageFilms(1, 100).then((response) => {
      if (response.code === 1000 && response.result) setAllFilms(response.result.data);
    }).catch(() => toast.error(t('filmsLoadFailed')));
  }, [t]);

  // Picking a category narrows which films show up in the pickers below it - re-derive
  // that option list whenever the category (or the base film list) changes.
  useEffect(() => {
    if (categoryFilter === 'ALL') {
      setCategoryFilms(allFilms);
      return;
    }
    let cancelled = false;
    filmService.browseFilms({ category: categoryFilter, page: 1, size: 100 }).then((response) => {
      if (!cancelled && response.code === 1000 && response.result) setCategoryFilms(response.result.data);
    }).catch(() => toast.error(t('filmsLoadFailed')));
    return () => {
      cancelled = true;
    };
  }, [categoryFilter, allFilms, t]);

  // A previously picked film may not belong to the newly picked category anymore.
  useEffect(() => {
    setFilmFilter('ALL');
  }, [categoryFilter]);

  const filmThumbnails = useMemo(() => new Map(allFilms.map((f) => [f.id, f.thumbnailUrl])), [allFilms]);

  useEffect(() => {
    setPage(1);
  }, [filmFilter]);

  useEffect(() => {
    let cancelled = false;

    const loadEpisodes = async () => {
      setLoading(true);
      try {
        const response = await filmService.getEpisodesPage({
          page,
          size: ROWS_PER_PAGE,
          filmId: filmFilter !== 'ALL' ? filmFilter : undefined,
        });
        if (!cancelled && response.code === 1000 && response.result) {
          setEpisodes(response.result.data);
          setTotalElements(response.result.totalElement);
        }
      } catch (error: unknown) {
        console.error('Failed to load episodes:', error);
        if (!cancelled) toast.error(t('episodesLoadFailed'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    loadEpisodes();
    return () => {
      cancelled = true;
    };
  }, [page, filmFilter, t]);

  const filteredEpisodes = useMemo(() => episodes.filter((ep) => {
    if (videoFilter === 'PUBLISHED' && !ep.videoFileId) return false;
    if (videoFilter === 'MISSING' && ep.videoFileId) return false;
    return true;
  }), [episodes, videoFilter]);

  const openEditPage = (episode: EpisodeSummaryResponse) => {
    navigate(`/film/${episode.filmId}/upload-episode`, { state: { episode } });
  };

  const handleDelete = async (episode: EpisodeSummaryResponse) => {
    const confirmed = await confirm({
      title: t('deleteEpisode'),
      message: t('deleteEpisodeConfirm', { title: episode.title, season: episode.seasonNumber, episode: episode.episodeNumber }),
    });
    if (!confirmed) return;

    try {
      const response = await filmService.deleteEpisode(episode.id);
      if (response.code === 1000) {
        toast.success(t('episodeDeleted'));
        setEpisodes((current) => current.filter((item) => item.id !== episode.id));
        setTotalElements((current) => Math.max(0, current - 1));
      } else {
        toast.error(response.message || t('episodeDeleteFailed'));
      }
    } catch (error: unknown) {
      console.error('Failed to delete episode:', error);
      toast.error(t('episodeDeleteFailed'));
    }
  };

  const goToUploadForFilm = (filmId: string) => {
    navigate(`/film/${filmId}/upload-episode`, { state: { newUpload: true } });
  };

  const handleAddClick = () => {
    if (filmFilter !== 'ALL') {
      goToUploadForFilm(filmFilter);
    } else {
      setPickedFilm(null);
      setPickFilmDialogOpen(true);
    }
  };

  return (
    <Box>
      <Box sx={adminToolbarSx}>
        <FormControl size="small" sx={{ minWidth: 170, ...adminInputSx }}>
          <InputLabel id="category-filter-label">{t('filmType')}</InputLabel>
          <Select
            labelId="category-filter-label"
            label={t('filmType')}
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value as 'ALL' | FilmCategory)}
          >
            <MenuItem value="ALL">{t('allFilmTypes')}</MenuItem>
            <MenuItem value="SERIES">{t('seriesFilms')}</MenuItem>
            <MenuItem value="STANDALONE">{t('standaloneFilms')}</MenuItem>
            <MenuItem value="ANIMATION">{t('animationFilms')}</MenuItem>
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 200, ...adminInputSx }}>
          <InputLabel id="film-filter-label">{t('films')}</InputLabel>
          <Select
            labelId="film-filter-label"
            label={t('films')}
            value={filmFilter}
            onChange={(e) => setFilmFilter(e.target.value)}
          >
            <MenuItem value="ALL">{t('allFilms')}</MenuItem>
            {categoryFilms.map((f) => (
              <MenuItem key={f.id} value={f.id}>{f.title}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 170, ...adminInputSx }}>
          <InputLabel id="video-filter-label">{t('filterByStatus')}</InputLabel>
          <Select
            labelId="video-filter-label"
            label={t('filterByStatus')}
            value={videoFilter}
            onChange={(e) => setVideoFilter(e.target.value as 'ALL' | 'PUBLISHED' | 'MISSING')}
          >
            <MenuItem value="ALL">{t('allStatuses')}</MenuItem>
            <MenuItem value="PUBLISHED">{t('episodePublished')}</MenuItem>
            <MenuItem value="MISSING">{t('episodeMissingVideo')}</MenuItem>
          </Select>
        </FormControl>
        <Box sx={{ flexGrow: 1 }} />
        <Button variant="contained" startIcon={<Add />} onClick={handleAddClick} sx={{ borderRadius: '10px' }}>
          {t('addNewEpisode')}
        </Button>
      </Box>

      <TableContainer component={Paper} sx={adminTableContainerSx}>
        <Table sx={{ minWidth: 650 }}>
          <TableHead>
            <TableRow>
              <TableCell sx={adminHeaderCellSx}>{t('episodes')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('belongsToFilm')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('episode')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('durationMinutes')}</TableCell>
              <TableCell sx={adminHeaderCellSx}>{t('status')}</TableCell>
              <TableCell sx={{ ...adminHeaderCellSx, textAlign: 'right' }}>{t('actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 6 }}><CircularProgress size={28} /></TableCell>
              </TableRow>
            ) : filteredEpisodes.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 6 }}>{t('noRoomEpisodes')}</TableCell>
              </TableRow>
            ) : filteredEpisodes.map((episode) => (
              <TableRow key={episode.id} hover>
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                    <Avatar variant="rounded" src={filmThumbnails.get(episode.filmId) || undefined} sx={{ width: 44, height: 44 }} />
                    <Box sx={{ fontWeight: 600 }}>{episode.title}</Box>
                  </Box>
                </TableCell>
                <TableCell>{episode.filmTitle}</TableCell>
                <TableCell>{t('episodeBadge', { episode: episode.episodeNumber, total: episode.filmEpisodeCount })}</TableCell>
                <TableCell>{t('minutes', { count: episode.durationMinutes })}</TableCell>
                <TableCell>
                  <Pill
                    label={episode.videoFileId ? t('episodePublished') : t('episodeMissingVideo')}
                    tone={episode.videoFileId ? 'success' : 'default'}
                    dot
                  />
                </TableCell>
                <TableCell align="right">
                  <IconButton color="primary" title={t('editAndReplaceVideo')} onClick={() => openEditPage(episode)}>
                    <Edit fontSize="small" />
                  </IconButton>
                  <IconButton color="error" title={t('delete')} onClick={() => handleDelete(episode)}>
                    <Delete fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        {!videoFilterActive ? (
          <AdminPagination page={page} rowsPerPage={ROWS_PER_PAGE} totalElements={totalElements} onPageChange={setPage} itemLabel={t('episodes').toLowerCase()} />
        ) : (
          <Box sx={{ px: 2.5, py: 2, borderTop: '1px solid', borderColor: 'divider', fontSize: '0.85rem', color: 'text.secondary' }}>
            {t('showingFilms', { count: filteredEpisodes.length })}
          </Box>
        )}
      </TableContainer>

      <Dialog open={pickFilmDialogOpen} onClose={() => setPickFilmDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('addNewEpisode')}</DialogTitle>
        <DialogContent sx={{ pt: '8px !important' }}>
          <Autocomplete
            options={categoryFilms}
            getOptionLabel={(film) => film.title}
            value={pickedFilm}
            onChange={(_event, value) => setPickedFilm(value)}
            sx={{ mt: 1 }}
            renderInput={(params) => <TextField {...params} label={t('selectFilmToManageEpisodes')} size="small" autoFocus />}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPickFilmDialogOpen(false)}>{t('cancel')}</Button>
          <Button
            variant="contained"
            disabled={!pickedFilm}
            onClick={() => {
              if (pickedFilm) goToUploadForFilm(pickedFilm.id);
              setPickFilmDialogOpen(false);
            }}
          >
            {t('addNewEpisode')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default memo(EpisodesTab);
