import React, { useMemo, useState } from 'react';
import { Box, Dialog, DialogContent, IconButton, Typography } from '@mui/material';
import { Close, ChevronLeft, ChevronRight } from '@mui/icons-material';

interface PostCardImageProps {
  imageUrls: string[];
}

const PostCardImage: React.FC<PostCardImageProps> = ({ imageUrls }) => {
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const [lightboxIndex, setLightboxIndex] = useState(0);

  const urls = useMemo(() => (imageUrls || []).filter(Boolean), [imageUrls]);
  if (urls.length === 0) return null;

  const total = urls.length;
  const shown = Math.min(total, 4);
  const moreCount = total - shown;

  const openAt = (i: number) => {
    setLightboxIndex(i);
    setLightboxOpen(true);
  };

  const next = () => setLightboxIndex((i) => (i + 1) % total);
  const prev = () => setLightboxIndex((i) => (i - 1 + total) % total);

  const cell = (url: string, onClick?: () => void, extraSx: any = {}, overlay?: React.ReactNode) => (
    <Box
      onClick={onClick}
      sx={{
        position: 'relative',
        cursor: onClick ? 'zoom-in' : 'default',
        backgroundImage: `url(${url})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
        bgcolor: 'action.hover',
        borderRadius: 1.5,
        overflow: 'hidden',
        ...extraSx,
      }}
    >
      {overlay}
    </Box>
  );

  const imgBox = (url: string, idx: number) =>
    cell(url, () => openAt(idx));

  let grid: React.ReactNode = null;

  if (total === 1) {
    grid = cell(urls[0], () => openAt(0), { aspectRatio: '16 / 9' });
  } else if (total === 2) {
    grid = (
      <Box className="grid grid-cols-2 gap-1" sx={{ aspectRatio: '2 / 1' }}>
        {imgBox(urls[0], 0)}
        {imgBox(urls[1], 1)}
      </Box>
    );
  } else if (total === 3) {
    grid = (
      <Box className="grid grid-cols-3 gap-1" sx={{ aspectRatio: '3 / 2' }}>
        <Box sx={{ aspectRatio: '3 / 4', gridRow: 'span 2' }}>{imgBox(urls[0], 0)}</Box>
        <Box sx={{ aspectRatio: '1 / 1' }}>{imgBox(urls[1], 1)}</Box>
        <Box sx={{ aspectRatio: '1 / 1' }}>{imgBox(urls[2], 2)}</Box>
      </Box>
    );
  } else {
    grid = (
      <Box className="grid grid-cols-3 gap-1" sx={{ aspectRatio: '3 / 2' }}>
        <Box sx={{ aspectRatio: '3 / 4', gridRow: 'span 2' }}>{imgBox(urls[0], 0)}</Box>
        <Box sx={{ aspectRatio: '1 / 1' }}>{imgBox(urls[1], 1)}</Box>
        <Box sx={{ aspectRatio: '1 / 1' }}>{imgBox(urls[2], 2)}</Box>
        <Box sx={{ aspectRatio: '1 / 1' }}>
          {total === 4 ? (
            imgBox(urls[3], 3)
          ) : (
            cell(
              urls[3],
              () => openAt(3),
              { aspectRatio: '1 / 1' },
              <Box
                sx={{
                  position: 'absolute',
                  inset: 0,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  bgcolor: 'rgba(0,0,0,0.55)',
                  color: '#fff',
                }}
              >
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  +{moreCount}
                </Typography>
              </Box>
            )
          )}
        </Box>
      </Box>
    );
  }

  return (
    <>
      {grid}

      <Dialog
        open={lightboxOpen}
        onClose={() => setLightboxOpen(false)}
        maxWidth="lg"
        fullWidth
        slotProps={{ paper: { sx: { bgcolor: '#000' } } }}
      >
        <Box
          sx={{
            position: 'relative',
            width: '100%',
            minHeight: 60,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            px: 1,
            py: 0.5,
            color: '#fff',
          }}
        >
          <Typography variant="caption" sx={{ opacity: 0.8 }}>
            {lightboxIndex + 1} / {total}
          </Typography>
          <IconButton onClick={() => setLightboxOpen(false)} sx={{ color: '#fff' }} size="small">
            <Close />
          </IconButton>
        </Box>
        <DialogContent sx={{ p: 0 }}>
          <Box
            sx={{
              position: 'relative',
              width: '100%',
              minHeight: 360,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              bgcolor: '#000',
            }}
          >
            {total > 1 && (
              <IconButton
                onClick={prev}
                sx={{
                  position: 'absolute',
                  left: 8,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  color: '#fff',
                  bgcolor: 'rgba(0,0,0,0.35)',
                  '&:hover': { bgcolor: 'rgba(0,0,0,0.55)' },
                  zIndex: 2,
                }}
                size="large"
              >
                <ChevronLeft fontSize="large" />
              </IconButton>
            )}
            <Box
              component="img"
              src={urls[lightboxIndex]}
              alt=""
              sx={{ maxWidth: '100%', maxHeight: '80vh', display: 'block' }}
            />
            {total > 1 && (
              <IconButton
                onClick={next}
                sx={{
                  position: 'absolute',
                  right: 8,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  color: '#fff',
                  bgcolor: 'rgba(0,0,0,0.35)',
                  '&:hover': { bgcolor: 'rgba(0,0,0,0.55)' },
                  zIndex: 2,
                }}
                size="large"
              >
                <ChevronRight fontSize="large" />
              </IconButton>
            )}
          </Box>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default PostCardImage;
