import React, { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Container,
  Grid as MuiGrid,
  Avatar,
  Chip as MuiChip,
  Rating,
  Button,
  Divider,
  CircularProgress,
  IconButton,
  Paper,
  alpha,
  useTheme,
  Tooltip,
  Stack,
  ButtonBase,
  Dialog,
} from '@mui/material';
import {
  PlayArrow,
  Add,
  Check,
  MovieOutlined,
  Share,
  CalendarToday,
  Public,
  AccessTime,
  Star,
  ArrowUpward,
  ArrowDownward,
  FormatListBulleted,
  Close,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import type { EpisodeResponse, FilmDetailResponse, FilmStatus } from '../../models';
import toast from 'react-hot-toast';
import CommentSection from '../../components/Comment/CommentSection';
import { extractYouTubeVideoId } from '../../utils/youtube';

const Chip = MuiChip;
const Grid = MuiGrid;

const FilmDetail: React.FC = () => {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<FilmDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [userRating, setUserRating] = useState<number | null>(null);
  const [followed, setFollowed] = useState(false);
  const [episodes, setEpisodes] = useState<EpisodeResponse[]>([]);
  const [selectedSeason, setSelectedSeason] = useState<number>(1);
  const [sortOrder, setSortOrder] = useState<'ASC' | 'DESC'>('ASC');
  const [trailerModalOpen, setTrailerModalOpen] = useState(false);
  const [descExpanded, setDescExpanded] = useState(false);
  const theme = useTheme();
  const navigate = useNavigate();

  useEffect(() => {
    const fetchDetail = async () => {
      if (!id) return;
      try {
        const [detailResponse, episodesResponse] = await Promise.all([
          filmService.getFilmAggregate(id),
          filmService.getEpisodesByFilm(id),
        ]);
        setData(detailResponse.result);
        setUserRating(detailResponse.result.userRating || null);
        setFollowed(detailResponse.result.followed || false);
        setEpisodes(episodesResponse.result ?? []);
      } catch (error) {
        console.error('Failed to fetch film details:', error);
        toast.error('Không thể tải thông tin phim');
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [id]);

  const seasons = useMemo(() => {
    if (!episodes.length) return [];
    const set = new Set<number>();
    episodes.forEach((e) => set.add(e.seasonNumber));
    return Array.from(set).sort((a, b) => a - b);
  }, [episodes]);

  const sortedEpisodesNatural = useMemo(() => {
    return [...episodes].sort((a, b) =>
      a.seasonNumber === b.seasonNumber
        ? a.episodeNumber - b.episodeNumber
        : a.seasonNumber - b.seasonNumber,
    );
  }, [episodes]);

  const episodesInSelectedSeason = useMemo(() => {
    const list = episodes.filter((e) => e.seasonNumber === selectedSeason);
    return [...list].sort((a, b) =>
      sortOrder === 'ASC' ? a.episodeNumber - b.episodeNumber : b.episodeNumber - a.episodeNumber,
    );
  }, [episodes, selectedSeason, sortOrder]);

  useEffect(() => {
    if (!seasons.length) return;
    if (!seasons.includes(selectedSeason)) {
      setSelectedSeason(seasons[0]);
    }
  }, [seasons, selectedSeason]);

  const handleRating = async (newValue: number | null) => {
    if (!id || newValue === null) return;
    try {
      await filmService.rateFilm(id, newValue);
      setUserRating(newValue);
      toast.success('Đã cập nhật đánh giá');
    } catch (error) {
      toast.error('Không thể cập nhật đánh giá');
    }
  };

  const handleFollow = async () => {
    if (!id) return;
    try {
      await filmService.processFollowAction(id, followed);
      setFollowed(!followed);
      window.dispatchEvent(new Event('sidebar-counts:refresh'));
      toast.success(followed ? 'Đã xóa khỏi thư viện' : 'Đã thêm vào thư viện');
    } catch (error) {
      toast.error('Không thể cập nhật trạng thái theo dõi');
    }
  };

  const handleWatch = () => {
    if (sortedEpisodesNatural.length > 0) {
      navigate(`/film/${id}/watch/${sortedEpisodesNatural[0].id}`);
      return;
    }
    if (extractYouTubeVideoId(data?.film.trailerUrl)) {
      setTrailerModalOpen(true);
      return;
    }
    // Phim cũ có thể còn trailerUrl là link video B2 (trước khi đổi sang YouTube) - vẫn mở được qua tab mới.
    if (data?.film.trailerUrl) {
      window.open(data.film.trailerUrl, '_blank', 'noopener,noreferrer');
      return;
    }
    toast.error('Phim chưa có tập hoặc trailer để phát');
  };

  const handleShare = async () => {
    const shareData = { title: data?.film.title ?? 'Aireak Film', url: window.location.href };
    try {
      if (navigator.share) {
        await navigator.share(shareData);
      } else {
        await navigator.clipboard.writeText(window.location.href);
        toast.success('Đã sao chép liên kết phim');
      }
    } catch (shareError) {
      console.error('Failed to share film:', shareError);
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '80vh' }}>
        <CircularProgress color="primary" />
      </Box>
    );
  }

  if (!data) {
    return (
      <Container sx={{ mt: 4 }}>
        <Typography variant="h5">Không tìm thấy phim</Typography>
        <Button onClick={() => navigate('/film')}>Quay lại</Button>
      </Container>
    );
  }

  const { film } = data;
  const trailerVideoId = extractYouTubeVideoId(film.trailerUrl);

  const STATUS_LABEL: Record<FilmStatus, string> = {
    ONGOING: 'Đang cập nhật',
    COMPLETED: 'Hoàn thành',
  };

  const getStatusColor = (status: FilmStatus) => {
    switch (status) {
      case 'ONGOING': return 'success';
      case 'COMPLETED': return 'default';
      default: return 'default';
    }
  };

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '100vh' }}>
      {/* Backdrop Section */}
      <Box 
        sx={{ 
          height: '60vh', 
          width: '100%', 
          position: 'relative',
          backgroundImage: `linear-gradient(to top, ${theme.palette.background.default} 0%, transparent 100%), url(${film.thumbnailUrl})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center center',
          display: 'flex',
          alignItems: 'flex-end',
          pb: 4
        }}
      >
        <Container maxWidth="lg">
          <Grid container spacing={4} sx={{alignItems: 'flex-end',}}>
            <Grid size={{ xs: 12, md: 3 }} sx={{ display: { xs: 'none', md: 'block' } }}>
              <Paper 
                elevation={12}
                sx={{ 
                  borderRadius: 4, 
                  overflow: 'hidden',
                  border: '2px solid rgba(255,255,255,0.1)'
                }}
              >
                <Box 
                  component="img"
                  src={film.thumbnailUrl}
                  sx={{ width: '100%', display: 'block' }}
                />
              </Paper>
            </Grid>
            <Grid size={{ xs: 12, md: 9 }}>
              <Box sx={{ mb: 2 }}>
                <Typography variant="h2" sx={{ fontWeight: 900, mb: 1, textShadow: '0 2px 10px rgba(0,0,0,0.5)' }}>
                  {film.title}
                </Typography>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
                  {film.status && (
                    <Chip 
                      label={STATUS_LABEL[film.status]}
                      color={getStatusColor(film.status)} 
                      size="small" 
                      sx={{ fontWeight: 700 }} 
                    />
                  )}
                  {film.genres.map(genre => (
                    <Chip 
                      key={genre} 
                      label={genre} 
                      size="small" 
                      sx={{ bgcolor: alpha(theme.palette.primary.main, 0.2), color: 'primary.light', fontWeight: 600 }} 
                    />
                  ))}
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, flexWrap: 'wrap' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <Star sx={{ color: '#FFD700' }} />
                    <Typography sx={{ fontWeight: 700, fontSize: '1.2rem' }}>{film.averageRating.toFixed(1)}</Typography>
                    <Typography variant="caption" sx={{ color: 'text.secondary', ml: 0.5 }}>({film.ratingCount} đánh giá)</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.secondary' }}>
                    <CalendarToday fontSize="small" />
                    <Typography variant="body2">{new Date(film.releaseDate).getFullYear()}</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.secondary' }}>
                    <AccessTime fontSize="small" />
                    <Typography variant="body2">{film.durationMinutes} phút</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.secondary' }}>
                    <Public fontSize="small" />
                    <Typography variant="body2">{film.country}</Typography>
                  </Box>
                </Box>
              </Box>

              <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                <Button 
                  variant="contained" 
                  size="large" 
                  startIcon={<PlayArrow />}
                  onClick={handleWatch}
                  sx={{ borderRadius: 2, px: 4, py: 1.2 }}
                >
                  {episodes.length > 0 ? t('watchNow') : 'Xem trailer'}
                </Button>
                <Button 
                  variant="outlined" 
                  size="large" 
                  startIcon={followed ? <Check /> : <Add />}
                  onClick={handleFollow}
                  sx={{ 
                    borderRadius: 2, 
                    px: 3, 
                    borderColor: followed ? 'primary.main' : 'rgba(255,255,255,0.3)',
                    color: followed ? 'primary.main' : 'white'
                  }}
                >
                  {followed ? 'Đã lưu vào thư viện' : 'Thêm vào thư viện'}
                </Button>
                {film.trailerUrl && (
                  <Button
                    variant="outlined"
                    size="large"
                    startIcon={<MovieOutlined />}
                    onClick={() =>
                      trailerVideoId
                        ? setTrailerModalOpen(true)
                        : window.open(film.trailerUrl, '_blank', 'noopener,noreferrer')
                    }
                    sx={{
                      borderRadius: 2,
                      px: 3,
                      borderColor: 'rgba(255,255,255,0.3)',
                      color: 'white',
                      '&:hover': { borderColor: 'white', bgcolor: 'rgba(255,255,255,0.1)' },
                    }}
                  >
                    Xem trailer
                  </Button>
                )}
                <IconButton onClick={handleShare} sx={{ bgcolor: 'rgba(255,255,255,0.1)', color: 'white' }}>
                  <Share />
                </IconButton>
              </Box>
            </Grid>
          </Grid>
        </Container>
      </Box>

      <Container maxWidth="lg" sx={{ py: 6 }}>
        <Grid container spacing={6}>
          <Grid size={{ xs: 12, md: 8 }}>
            <Box sx={{ mb: 6 }}>
              <Typography variant="h5" sx={{ fontWeight: 700, mb: 2 }}>{t('storyline')}</Typography>
              <Typography
                variant="body1"
                sx={{
                  color: 'text.secondary',
                  lineHeight: 1.8,
                  whiteSpace: 'pre-line',
                  ...(descExpanded
                    ? {}
                    : {
                        display: '-webkit-box',
                        WebkitLineClamp: 5,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                      }),
                }}
              >
                {film.description}
              </Typography>
              {(film.description?.length ?? 0) > 220 && (
                <Button
                  size="small"
                  onClick={() => setDescExpanded((prev) => !prev)}
                  sx={{ mt: 0.5, px: 0, minWidth: 0, textTransform: 'none', fontWeight: 700 }}
                >
                  {descExpanded ? 'Thu gọn' : 'Xem thêm'}
                </Button>
              )}
            </Box>

            <Box sx={{ mb: 6 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2.5, flexWrap: 'wrap', gap: 1 }}>
                <Typography variant="h5" sx={{ fontWeight: 800, display: 'flex', alignItems: 'center', gap: 1 }}>
                  <FormatListBulleted sx={{ color: 'primary.main' }} />
                  {'CHỌN TẬP'}
                </Typography>
                {episodes.length > 0 && (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Chip
                      size="small"
                      label={sortOrder === 'ASC' ? 'Tập tăng dần' : 'Tập giảm dần'}
                      sx={{ bgcolor: 'rgba(255,255,255,0.05)', fontWeight: 700, letterSpacing: 0.2 }}
                    />
                    <Tooltip title={sortOrder === 'ASC' ? 'Chuyển sắp xếp giảm dần' : 'Chuyển sắp xếp tăng dần'}>
                      <IconButton
                        size="small"
                        onClick={() => setSortOrder((prev) => (prev === 'ASC' ? 'DESC' : 'ASC'))}
                        sx={{
                          bgcolor: 'rgba(255,255,255,0.05)',
                          border: '1px solid rgba(255,255,255,0.08)',
                          '&:hover': { bgcolor: 'rgba(0,168,78,0.15)' },
                        }}
                      >
                        {sortOrder === 'ASC' ? <ArrowUpward fontSize="small" /> : <ArrowDownward fontSize="small" />}
                      </IconButton>
                    </Tooltip>
                  </Box>
                )}
              </Box>

              {episodes.length === 0 ? (
                <Paper sx={{ p: 3, borderRadius: 3, bgcolor: 'rgba(255,255,255,0.03)', color: 'text.secondary' }}>
                  Chưa có tập phim nào được phát hành.
                </Paper>
              ) : (
                <>
                  {seasons.length > 1 && (
                    <Stack direction="row" spacing={1.5} sx={{ mb: 3, flexWrap: 'wrap', rowGap: 1 }}>
                      {seasons.map((sn) => {
                        const isActive = selectedSeason === sn;
                        return (
                          <Chip
                            key={sn}
                            label={`Phần ${sn}`}
                            clickable
                            onClick={() => setSelectedSeason(sn)}
                            color={isActive ? 'primary' : 'default'}
                            variant={isActive ? 'filled' : 'outlined'}
                            sx={{
                              fontWeight: 800,
                              fontSize: '0.95rem',
                              px: 0.5,
                              py: 2.25,
                              borderRadius: 1.25,
                              letterSpacing: 0.15,
                              border: isActive ? 'none' : '1px solid rgba(255,255,255,0.12)',
                              bgcolor: isActive ? alpha(theme.palette.primary.main, 0.92) : 'rgba(255,255,255,0.025)',
                              '&:hover': {
                                bgcolor: isActive ? alpha(theme.palette.primary.main, 1) : 'rgba(255,255,255,0.06)',
                              },
                            }}
                          />
                        );
                      })}
                    </Stack>
                  )}

                  <Paper sx={{ borderRadius: 3, overflow: 'hidden', bgcolor: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)', p: 2 }}>
                    {episodesInSelectedSeason.length === 0 ? (
                      <Box sx={{ p: 4, color: 'text.secondary', textAlign: 'center' }}>
                        Phần này chưa có tập phim nào.
                      </Box>
                    ) : (
                      <MuiGrid container spacing={1.25}>
                        {episodesInSelectedSeason.map((episode) => {
                          return (
                            <MuiGrid size={{ xs: 4, sm: 3, md: 3, lg: 2, xl: 2 }} key={episode.id}>
                              <ButtonBase
                                onClick={() => navigate(`/film/${id}/watch/${episode.id}`)}
                                sx={{
                                  width: '100%',
                                  aspectRatio: '2.25 / 1',
                                  borderRadius: 1.5,
                                  bgcolor: 'rgba(255,255,255,0.04)',
                                  color: 'text.primary',
                                  position: 'relative',
                                  fontWeight: 900,
                                  fontSize: { xs: '0.95rem', sm: '1.05rem', md: '1.15rem' },
                                  letterSpacing: 0.3,
                                  transition: 'all 0.18s cubic-bezier(.2,.8,.2,1)',
                                  border: '1px solid rgba(255,255,255,0.08)',
                                  '&:hover': {
                                    bgcolor: 'rgba(0,168,78,0.12)',
                                    transform: 'translateY(-1.5px)',
                                    boxShadow: '0 4px 14px rgba(0,0,0,0.35)',
                                    borderColor: alpha(theme.palette.primary.main, 0.5),
                                  },
                                }}
                              >
                                <Typography
                                  component="span"
                                  sx={{
                                    fontWeight: 900,
                                    fontSize: { xs: '0.95rem', sm: '1.05rem', md: '1.15rem' },
                                    letterSpacing: 0.3,
                                  }}
                                >
                                  {episode.episodeNumber}
                                </Typography>
                              </ButtonBase>
                            </MuiGrid>
                          );
                        })}
                      </MuiGrid>
                    )}
                  </Paper>
                </>
              )}
            </Box>

            <Box sx={{ mb: 6 }}>
              <Typography variant="h5" sx={{ fontWeight: 700, mb: 3 }}>{t('topCast')}</Typography>
              <Grid container spacing={2}>
                {film.casts.map((cast) => (
                  <Grid size={{ xs: 6, sm: 4 }} key={cast.id}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                      <Avatar 
                        src={cast.actor.avatarUrl} 
                        sx={{ width: 56, height: 56, border: '2px solid rgba(255,255,255,0.1)' }} 
                      />
                      <Box>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{cast.actor.name}</Typography>
                        <Typography variant="caption" color="text.secondary">{cast.characterName}</Typography>
                      </Box>
                    </Box>
                  </Grid>
                ))}
              </Grid>
            </Box>
          </Grid>

          <Grid size={{ xs: 12, md: 4 }}>
            <Paper sx={{ p: 3, borderRadius: 4, bgcolor: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.05)' }}>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>{t('director')}</Typography>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mb: 4 }}>
                {[...film.directors]
                  .sort((a, b) => a.displayOrder - b.displayOrder)
                  .map(({ director }) => (
                    <Box key={director.id} sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                      <Avatar src={director.avatarUrl} sx={{ width: 64, height: 64 }} />
                      <Box>
                        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{director.name}</Typography>
                        <Typography variant="caption" color="text.secondary">Đạo diễn</Typography>
                      </Box>
                    </Box>
                  ))}
              </Box>

              <Divider sx={{ my: 3 }} />

              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>{t('yourRating')}</Typography>
              <Box sx={{ textAlign: 'center', py: 2 }}>
                <Rating
                  name="user-rating"
                  value={userRating}
                  precision={1}
                  size="large"
                  onChange={(_, newValue) => handleRating(newValue)}
                  emptyIcon={<Star style={{ opacity: 0.2, color: 'white' }} fontSize="inherit" />}
                />
                <Typography variant="body2" sx={{ mt: 1, color: 'text.secondary' }}>
                  {userRating ? `${t('yourRating')} ${userRating}/5` : t('rateThisFilm')}
                </Typography>
              </Box>

              <Divider sx={{ my: 3 }} />

              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>Chi tiết</Typography>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="body2" color="text.secondary">Quốc gia</Typography>
                  <Typography variant="body2">{film.country}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="body2" color="text.secondary">Loại phim</Typography>
                  <Typography variant="body2">{film.series ? 'Phim bộ' : 'Phim lẻ'}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="body2" color="text.secondary">Cập nhật lần cuối</Typography>
                  <Typography variant="body2">{new Date(film.lastUpdate).toLocaleDateString('vi-VN')}</Typography>
                </Box>
              </Box>
            </Paper>
          </Grid>
        </Grid>

        <Box sx={{ mt: 8 }}>
          <Typography variant="h5" sx={{ fontWeight: 700, mb: 3 }}>Bình luận</Typography>
          <CommentSection sourceId={id || ''} />
        </Box>
      </Container>

      <Dialog
        open={trailerModalOpen}
        onClose={() => setTrailerModalOpen(false)}
        maxWidth="md"
        fullWidth
        slotProps={{ paper: { sx: { bgcolor: 'black', position: 'relative' } } }}
      >
        <IconButton
          onClick={() => setTrailerModalOpen(false)}
          sx={{
            position: 'absolute',
            top: 8,
            right: 8,
            zIndex: 1,
            color: 'white',
            bgcolor: 'rgba(0,0,0,0.5)',
            '&:hover': { bgcolor: 'rgba(0,0,0,0.7)' },
          }}
        >
          <Close />
        </IconButton>
        {/* Chỉ mount iframe khi modal mở để video dừng phát ngay khi đóng, không cần gọi postMessage API. */}
        {trailerModalOpen && trailerVideoId && (
          <Box sx={{ position: 'relative', pt: '56.25%' }}>
            <Box
              component="iframe"
              src={`https://www.youtube.com/embed/${trailerVideoId}?autoplay=1`}
              title={`${film.title} - Trailer`}
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
              sx={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', border: 0 }}
            />
          </Box>
        )}
      </Dialog>
    </Box>
  );
};

export default FilmDetail;
