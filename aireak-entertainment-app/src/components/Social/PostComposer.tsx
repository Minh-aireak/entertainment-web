import React, { useCallback, useState } from 'react';
import { Avatar, Box, Button, Card, CardContent, Collapse, TextField } from '@mui/material';
import { Send } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

import { postService } from '../../api/postService';
import { getAvatarGradient } from '../../utils/avatarColor';
import type { Post } from './types';

const POST_TYPE = 'BUSINESS_SCHEDULE';

interface PostComposerProps {
  avatar?: string;
  displayName?: string;
  onPostCreated: (post: Post) => void;
}

const PostComposer: React.FC<PostComposerProps> = ({ avatar, displayName, onPostCreated }) => {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [posting, setPosting] = useState(false);

  const handleSubmit = useCallback(async () => {
    if (!content.trim()) return;
    setPosting(true);
    try {
      const now = new Date();
      const farFuture = new Date(now.getFullYear() + 10, now.getMonth(), now.getDate());
      const res = await postService.createPost({
        postType: POST_TYPE,
        title: title.trim() || t('untitledPost'),
        content: content.trim(),
        startTime: now.toISOString(),
        endTime: farFuture.toISOString(),
      });
      if (res.data.code === 1000 && res.data.result) {
        onPostCreated(res.data.result);
        setTitle('');
        setContent('');
        setExpanded(false);
        toast.success(t('postCreated'));
      }
    } catch (error) {
      console.error('Failed to create post:', error);
      toast.error(t('postCreateFailed'));
    } finally {
      setPosting(false);
    }
  }, [content, title, t, onPostCreated]);

  const handleCancel = () => {
    setTitle('');
    setContent('');
    setExpanded(false);
  };

  return (
    <Card
      elevation={0}
      sx={{
        borderRadius: 3,
        mb: 2,
        transition: 'box-shadow 200ms ease',
        '&:focus-within': { boxShadow: '0 0 0 2px rgba(0, 168, 78, 0.35)' },
      }}
    >
      <CardContent>
        <Box className="flex gap-2" sx={{ alignItems: expanded ? 'flex-start' : 'center' }}>
          <Avatar
            src={avatar || undefined}
            slotProps={{ img: { loading: 'lazy' } }}
            sx={{ background: avatar ? undefined : getAvatarGradient(displayName), color: '#fff', fontWeight: 700 }}
          >
            {displayName?.[0]?.toUpperCase() || '?'}
          </Avatar>
          <Box className="flex flex-1 flex-col gap-2" sx={{ minWidth: 0 }}>
            <Collapse in={expanded} timeout={200} unmountOnExit>
              <TextField
                size="small"
                placeholder={t('postTitlePlaceholder')}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                fullWidth
                className="mb-2"
              />
            </Collapse>
            <TextField
              size="small"
              placeholder={t('postContentPlaceholder')}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              onFocus={() => setExpanded(true)}
              multiline={expanded}
              minRows={expanded ? 2 : 1}
              fullWidth
              sx={{
                '& .MuiOutlinedInput-root': {
                  borderRadius: expanded ? 2 : 999,
                  transition: 'border-radius 160ms ease',
                },
              }}
            />
            <Collapse in={expanded} timeout={200} unmountOnExit>
              <Box className="mt-1 flex justify-end gap-2">
                {(title || content) && (
                  <Button size="small" color="inherit" onClick={handleCancel} disabled={posting}>
                    {t('cancel')}
                  </Button>
                )}
                <Button
                  variant="contained"
                  size="small"
                  endIcon={<Send fontSize="small" />}
                  disabled={!content.trim() || posting}
                  onClick={handleSubmit}
                >
                  {posting ? t('posting') : t('postSubmit')}
                </Button>
              </Box>
            </Collapse>
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
};

export default PostComposer;
