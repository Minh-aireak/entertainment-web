import React from 'react';
import { Typography, Grid, Paper, Box, Button, Card, CardContent, CardActions } from '@mui/material';
import { Chat, People, Person } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

const SocialHome: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const features = [
    {
      title: t('chat'),
      description: t('socialChatDesc'),
      icon: <Chat sx={{ fontSize: 40, color: 'primary.main' }} />,
      path: '/social/chat',
    },
    {
      title: t('friends'),
      description: t('socialFriendsDesc'),
      icon: <People sx={{ fontSize: 40, color: 'secondary.main' }} />,
      path: '/social/friends',
    },
    {
      title: t('profile'),
      description: t('socialProfileDesc'),
      icon: <Person sx={{ fontSize: 40, color: 'success.main' }} />,
      path: '/social/profile',
    },
  ];

  return (
    <Box>
      <Paper
        elevation={0}
        sx={{
          p: 4,
          mb: 4,
          background: 'linear-gradient(135deg, rgba(40, 167, 69, 0.12) 0%, rgba(30, 30, 30, 0.8) 100%)',
          border: '1px solid rgba(255, 255, 255, 0.08)',
          borderRadius: 2,
        }}
      >
        <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 600 }}>
          {t('socialHomeSubtitle')}
        </Typography>
      </Paper>

      <Grid container spacing={3}>
        {features.map((feature) => (
          <Grid key={feature.path} size={{ xs: 12, sm: 6, md: 4 }}>
            <Card
              sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                transition: 'transform 0.2s',
                '&:hover': { transform: 'translateY(-4px)' },
              }}
            >
              <CardContent sx={{ flexGrow: 1, textAlign: 'center', pt: 4 }}>
                <Box sx={{ mb: 2 }}>{feature.icon}</Box>
                <Typography variant="h6" gutterBottom>
                  {feature.title}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {feature.description}
                </Typography>
              </CardContent>
              <CardActions sx={{ justifyContent: 'center', pb: 2 }}>
                <Button size="small" variant="contained" onClick={() => navigate(feature.path)}>
                  {t('explore')}
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  );
};

export default SocialHome;
