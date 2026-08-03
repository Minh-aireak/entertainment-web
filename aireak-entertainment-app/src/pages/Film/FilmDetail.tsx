import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { 
  Box, 
  Typography, 
  Container, 
  Grid, 
  Avatar, 
  Chip, 
  Rating, 
  Button, 
  Divider,
  CircularProgress,
  IconButton,
  Paper,
  alpha,
  useTheme,
  Dialog,
  DialogContent,
  List,
  ListItemButton,
  ListItemText,
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
  Close,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import type { EpisodeResponse, FilmDetailResponse, FilmStatus } from '../../models';
import toast from 'react-hot-toast';
import CommentSection from '../../components/Comment/CommentSection';

const FilmDetail: React.FC = () => {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<FilmDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [userRating, setUserRating] = useState<number | null>(null);
  const [followed, setFollowed] = useState(false);
  const [episodes, setEpisodes] = useState<EpisodeResponse[]>([]);
  const [selectedEpisode, setSelectedEpisode] = useState<EpisodeResponse | null>(null);
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
        toast.error('Failed to load film details');
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [id]);

  const handleRating = async (newValue: number | null) => {
    if (!id || newValue === null) return;
    try {
      await filmService.rateFilm(id, newValue);
      setUserRating(newValue);
      toast.success('Rating updated');
    } catch (error) {
      toast.error('Failed to update rating');
    }
  };

  const handleFollow = async () => {
    if (!id) return;
    try {
      await filmService.processFollowAction(id, followed);
      setFollowed(!followed);
      window.dispatchEvent(new Event('sidebar-counts:refresh'));
      toast.success(followed ? 'Removed from library' : 'Added to library');
    } catch (error) {
      toast.error('Failed to update follow status');
    }
  };

  const handleWatch = () => {
    if (episodes.length > 0) {
      setSelectedEpisode(episodes[0]);
      return;
    }
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
        <Typography variant="h5">Film not found</Typography>
        <Button onClick={() => navigate('/film')}>Go Back</Button>
      </Container>
    );
  }

  const { film } = data;

  const getStatusColor = (status: FilmStatus) => {
    switch (status) {
      case 'NOW_PLAYING': return 'error';
      case 'UPCOMING': return 'primary';
      case 'ENDED': return 'default';
      case 'ARCHIVED': return 'default';
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
                      label={film.status.replace('_', ' ')} 
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
                    <Typography variant="caption" sx={{ color: 'text.secondary', ml: 0.5 }}>({film.ratingCount} reviews)</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.secondary' }}>
                    <CalendarToday fontSize="small" />
                    <Typography variant="body2">{new Date(film.releaseDate).getFullYear()}</Typography>
                  </Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.secondary' }}>
                    <AccessTime fontSize="small" />
                    <Typography variant="body2">{film.durationMinutes} min</Typography>
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
                  <IconButton onClick={() => window.open(film.trailerUrl, '_blank', 'noopener,noreferrer')} sx={{ bgcolor: 'rgba(255,255,255,0.1)', color: 'white' }}>
                    <MovieOutlined />
                  </IconButton>
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
              <Typography variant="body1" sx={{ color: 'text.secondary', lineHeight: 1.8 }}>
                {film.description}
              </Typography>
            </Box>

            <Box sx={{ mb: 6 }}>
              <Typography variant="h5" sx={{ fontWeight: 700, mb: 2 }}>
                {film.series ? 'Danh sách tập' : 'Nội dung phim'}
              </Typography>
              {episodes.length === 0 ? (
                <Paper sx={{ p: 3, borderRadius: 3, bgcolor: 'rgba(255,255,255,0.03)', color: 'text.secondary' }}>
                  Chưa có tập phim nào được phát hành.
                </Paper>
              ) : (
                <Paper sx={{ borderRadius: 3, overflow: 'hidden', bgcolor: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' }}>
                  <List disablePadding>
                    {episodes.map((episode, index) => (
                      <ListItemButton
                        key={episode.id}
                        onClick={() => setSelectedEpisode(episode)}
                        divider={index < episodes.length - 1}
                        sx={{ py: 1.5 }}
                      >
                        <PlayArrow color="primary" sx={{ mr: 2 }} />
                        <ListItemText
                          primary={episode.title}
                          secondary={`Mùa ${episode.seasonNumber} · Tập ${episode.episodeNumber} · ${episode.durationMinutes || '--'} phút`}
                        />
                      </ListItemButton>
                    ))}
                  </List>
                </Paper>
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
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
                <Avatar src={film.director.avatarUrl} sx={{ width: 64, height: 64 }} />
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>{film.director.name}</Typography>
                  <Typography variant="caption" color="text.secondary">Director</Typography>
                </Box>
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
                <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>Details</Typography>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="body2" color="text.secondary">Country</Typography>
                  <Typography variant="body2">{film.country}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="body2" color="text.secondary">Series</Typography>
                  <Typography variant="body2">{film.series ? 'Yes' : 'No'}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="body2" color="text.secondary">Last Update</Typography>
                  <Typography variant="body2">{new Date(film.lastUpdate).toLocaleDateString()}</Typography>
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

      <Dialog open={Boolean(selectedEpisode)} onClose={() => setSelectedEpisode(null)} maxWidth="lg" fullWidth>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 2, py: 1.5, bgcolor: '#101010' }}>
          <Box>
            <Typography sx={{ fontWeight: 800 }}>{selectedEpisode?.title}</Typography>
            <Typography variant="caption" color="text.secondary">
              Mùa {selectedEpisode?.seasonNumber} · Tập {selectedEpisode?.episodeNumber}
            </Typography>
          </Box>
          <IconButton onClick={() => setSelectedEpisode(null)}><Close /></IconButton>
        </Box>
        <DialogContent sx={{ p: 0, bgcolor: '#000' }}>
          {selectedEpisode && (
            <Box component="video" src={selectedEpisode.videoUrl} controls autoPlay sx={{ width: '100%', maxHeight: '75vh', display: 'block' }} />
          )}
        </DialogContent>
      </Dialog>
    </Box>
  );
};

export default FilmDetail;
