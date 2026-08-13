import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Avatar,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Collapse,
  Grid,
  IconButton,
  LinearProgress,
  Popover,
  TextField,
  Typography,
} from '@mui/material';
import {
  Send,
  Close,
  Image as ImageIcon,
  EmojiEmotions,
  Mood,
  Palette,
} from '@mui/icons-material';
import { useSelector } from 'react-redux';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import EmojiPicker, { Theme, type EmojiClickData } from 'emoji-picker-react';

import { postService } from '../../api/postService';
import { fileService } from '../../api/fileService';
import { getAvatarGradient } from '../../utils/avatarColor';
import { type RootState } from '../../store';
import { POST_BACKGROUNDS, POST_FEELINGS, BACKGROUND_TEXT_LIMIT } from './postComposerOptions';
import type {
  Post,
} from './types';
import type {
  PostRequest,
  PostResponse,
} from '../../models';

interface PendingUpload {
  key: string;
  name: string;
  previewUrl: string;
  fileId?: string;
  progress: number;
  error?: string;
}

const IMAGE_WORKER_COUNT = 2;
const FILE_INPUT_KEY = 'post-composer-image-input';

interface PostComposerProps {
  avatar?: string;
  displayName?: string;
  onPostCreated: (post: Post) => void;
}

const runUploadWorkers = async (
  _files: File[],
  onProgress: (key: string, progress: number) => void,
  onResolve: (key: string, fileId: string, finalUrl?: string) => void,
  onReject: (key: string, message: string) => void,
  pendingRef: React.MutableRefObject<Map<string, File>>,
  uploadFailedMessage: string,
) => {
  const entries = Array.from(pendingRef.current.entries());
  let cursor = 0;

  const worker = async () => {
    for (;;) {
      const idx = cursor++;
      if (idx >= entries.length) return;
      const [key, file] = entries[idx];
      onProgress(key, 0);
      try {
        const res = await fileService.uploadFile(file);
        onProgress(key, 100);
        if (res?.code === 1000 && res.result && (res.result as any).id) {
          const info = res.result as any;
          onResolve(key, info.id, info.url);
        } else {
          const msg = (res?.message as string) || uploadFailedMessage;
          onReject(key, msg);
        }
      } catch (err: any) {
        onReject(key, err?.message || uploadFailedMessage);
      }
    }
  };

  const workerCount = Math.min(IMAGE_WORKER_COUNT, entries.length) || 1;
  await Promise.all(Array.from({ length: workerCount }, worker));
};

const PostComposer: React.FC<PostComposerProps> = ({ avatar, displayName, onPostCreated }) => {
  const { t } = useTranslation();
  const themeMode = useSelector((state: RootState) => state.ui.themeMode);

  const [expanded, setExpanded] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [posting, setPosting] = useState(false);

  const [pendingUploads, setPendingUploads] = useState<PendingUpload[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const pendingRef = useRef<Map<string, File>>(new Map());
  const uploadLockRef = useRef(false);
  const contentInputRef = useRef<HTMLTextAreaElement | HTMLInputElement | null>(null);
  const dragKeyRef = useRef<string | null>(null);

  const [emojiAnchorEl, setEmojiAnchorEl] = useState<null | HTMLElement>(null);
  const [feelingAnchorEl, setFeelingAnchorEl] = useState<null | HTMLElement>(null);
  const [feelingKey, setFeelingKey] = useState<string | null>(null);
  const [backgroundKey, setBackgroundKey] = useState<string | null>(null);
  const [showBgStrip, setShowBgStrip] = useState(false);
  const [draggingKey, setDraggingKey] = useState<string | null>(null);

  const hasImages = pendingUploads.length > 0;

  useEffect(() => {
    if (hasImages) {
      setBackgroundKey(null);
      setShowBgStrip(false);
    }
  }, [hasImages]);

  useEffect(() => {
    if (backgroundKey && content.length > BACKGROUND_TEXT_LIMIT) {
      setBackgroundKey(null);
      toast(t('backgroundTextTooLong'), { icon: 'ℹ️' });
    }
  }, [content, backgroundKey, t]);

  const resetForm = useCallback(() => {
    setTitle('');
    setContent('');
    setPendingUploads([]);
    pendingRef.current.clear();
    setFeelingKey(null);
    setBackgroundKey(null);
    setShowBgStrip(false);
  }, []);

  const handleCancel = () => {
    resetForm();
    setExpanded(false);
  };

  const handleEmojiClick = (emojiData: EmojiClickData) => {
    const emoji = emojiData.emoji;
    const input = contentInputRef.current;
    const start = input?.selectionStart ?? content.length;
    const end = input?.selectionEnd ?? content.length;

    setContent(content.slice(0, start) + emoji + content.slice(end));

    requestAnimationFrame(() => {
      if (!input) return;
      const cursor = start + emoji.length;
      input.focus();
      input.setSelectionRange(cursor, cursor);
    });
  };

  const handlePickImages = (ev: React.ChangeEvent<HTMLInputElement>) => {
    const files = ev.target.files;
    if (!files || files.length === 0) return;
    const items: PendingUpload[] = [];
    for (let i = 0; i < files.length; i++) {
      const f = files[i];
      if (!f.type.startsWith('image/')) continue;
      const key = `${Date.now()}_${i}_${f.name}`;
      pendingRef.current.set(key, f);
      items.push({
        key,
        name: f.name,
        previewUrl: URL.createObjectURL(f),
        progress: 0,
      });
    }
    setPendingUploads((prev) => [...prev, ...items]);
    if (!expanded && items.length > 0) setExpanded(true);
    if (fileInputRef.current) fileInputRef.current.value = '';
    if (!uploadLockRef.current && items.length > 0) {
      uploadLockRef.current = true;
      runUploadWorkers(
        Array.from(pendingRef.current.values()),
        (key, progress) =>
          setPendingUploads((prev) => prev.map((p) => (p.key === key ? { ...p, progress } : p))),
        (key, fileId) => {
          setPendingUploads((prev) =>
            prev.map((p) => (p.key === key ? { ...p, fileId, error: undefined } : p))
          );
        },
        (key, message) => {
          setPendingUploads((prev) =>
            prev.map((p) => (p.key === key ? { ...p, error: message } : p))
          );
        },
        pendingRef,
        t('uploadFailed'),
      ).finally(() => {
        uploadLockRef.current = false;
      });
    }
  };

  const handleRemoveImage = (key: string) => {
    setPendingUploads((prev) => prev.filter((p) => p.key !== key));
    pendingRef.current.delete(key);
  };

  const handleImageDrop = (targetKey: string) => {
    const sourceKey = dragKeyRef.current;
    dragKeyRef.current = null;
    setDraggingKey(null);
    if (!sourceKey || sourceKey === targetKey) return;
    setPendingUploads((prev) => {
      const list = [...prev];
      const fromIdx = list.findIndex((p) => p.key === sourceKey);
      const toIdx = list.findIndex((p) => p.key === targetKey);
      if (fromIdx === -1 || toIdx === -1) return prev;
      const [moved] = list.splice(fromIdx, 1);
      list.splice(toIdx, 0, moved);
      return list;
    });
  };

  const imageCountValid = pendingUploads.filter((p) => p.fileId && !p.error).length > 0;
  const uploading = pendingUploads.some((p) => !p.fileId && !p.error);

  const avgProgress = useMemo(() => {
    if (pendingUploads.length === 0) return 0;
    const total = pendingUploads.reduce((s, p) => s + p.progress, 0);
    return Math.round(total / pendingUploads.length);
  }, [pendingUploads]);

  const canSubmitText = (title.trim() || content.trim()) !== '';
  const canSubmitImage = imageCountValid && !uploading;
  const canSubmit = hasImages ? canSubmitImage : canSubmitText;

  const buildPayload = (): PostRequest | null => {
    const baseTitle = title.trim() || undefined;
    const baseContent = content.trim() || undefined;

    if (hasImages) {
      const imageFileIds = pendingUploads
        .filter((p) => p.fileId && !p.error)
        .map((p) => p.fileId as string);
      if (imageFileIds.length === 0) return null;
      return {
        postType: 'IMAGE',
        title: baseTitle,
        content: baseContent,
        imageFileIds,
        feeling: feelingKey ?? undefined,
      };
    }

    if (!baseTitle && !baseContent) return null;
    return {
      postType: 'TEXT',
      title: baseTitle,
      content: baseContent,
      backgroundColor: backgroundKey ?? undefined,
      feeling: feelingKey ?? undefined,
    };
  };

  const handleSubmit = useCallback(async () => {
    const payload = buildPayload();
    if (!payload || posting) return;
    setPosting(true);
    try {
      const res = await postService.createPost(payload);
      if (res?.data?.code === 1000 && res.data.result) {
        onPostCreated(res.data.result as PostResponse as Post);
        resetForm();
        setExpanded(false);
        toast.success(t('postCreated'));
      } else {
        toast.error(res?.data?.message || t('postCreateFailed'));
      }
    } catch (e: any) {
      toast.error(e?.message || t('postCreateFailed'));
    } finally {
      setPosting(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [title, content, pendingUploads, feelingKey, backgroundKey, posting]);

  const activeBackground = POST_BACKGROUNDS.find((b) => b.key === backgroundKey);
  const activeFeeling = POST_FEELINGS.find((f) => f.key === feelingKey);
  const hideTitleField = Boolean(activeBackground);

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
      <CardContent sx={{ pb: 1.5 }}>
        <Box className="flex gap-2" sx={{ alignItems: expanded ? 'flex-start' : 'center' }}>
          <Avatar
            src={avatar || undefined}
            slotProps={{ img: { loading: 'lazy' } }}
            sx={{
              background: avatar ? undefined : getAvatarGradient(displayName),
              color: '#fff',
              fontWeight: 700,
            }}
          >
            {displayName?.[0]?.toUpperCase() || '?'}
          </Avatar>

          <Box className="flex flex-1 flex-col gap-2" sx={{ minWidth: 0 }}>
            {activeBackground ? (
              <Box
                sx={{
                  position: 'relative',
                  borderRadius: 3,
                  background: activeBackground.gradient,
                  minHeight: 200,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  p: 3,
                  transition: 'background 200ms ease',
                }}
              >
                <IconButton
                  size="small"
                  onClick={() => setBackgroundKey(null)}
                  sx={{
                    position: 'absolute',
                    top: 8,
                    right: 8,
                    bgcolor: 'rgba(0,0,0,0.25)',
                    color: '#fff',
                    '&:hover': { bgcolor: 'rgba(0,0,0,0.4)' },
                  }}
                >
                  <Close fontSize="small" />
                </IconButton>
                <TextField
                  inputRef={contentInputRef}
                  variant="standard"
                  placeholder={t('postContentPlaceholder')}
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  onFocus={() => setExpanded(true)}
                  multiline
                  fullWidth
                  slotProps={{ input: { disableUnderline: true } }}
                  sx={{
                    '& .MuiInputBase-input': {
                      color: activeBackground.textColor,
                      fontSize: { xs: 22, sm: 26 },
                      fontWeight: 800,
                      textAlign: 'center',
                      lineHeight: 1.35,
                    },
                    '& .MuiInputBase-input::placeholder': {
                      color: activeBackground.textColor,
                      opacity: 0.85,
                    },
                  }}
                />
              </Box>
            ) : (
              <TextField
                size="small"
                inputRef={contentInputRef}
                placeholder={t('postContentPlaceholder')}
                value={content}
                onChange={(e) => {
                  setContent(e.target.value);
                  if (!expanded && e.target.value) setExpanded(true);
                }}
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
            )}

            {activeBackground && (
              <Typography
                variant="caption"
                color={content.length > BACKGROUND_TEXT_LIMIT * 0.85 ? 'warning.main' : 'text.secondary'}
                sx={{ display: 'block', textAlign: 'right' }}
              >
                {content.length}/{BACKGROUND_TEXT_LIMIT}
              </Typography>
            )}

            <Collapse in={expanded} timeout={200} unmountOnExit>
              <Box className="flex flex-col gap-2">
                <Collapse in={!hideTitleField} timeout={150} unmountOnExit>
                  <TextField
                    size="small"
                    placeholder={t('postTitlePlaceholder')}
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    fullWidth
                  />
                </Collapse>

                <input
                  key={FILE_INPUT_KEY}
                  ref={fileInputRef}
                  id="post-composer-file"
                  type="file"
                  multiple
                  accept="image/*"
                  hidden
                  onChange={handlePickImages}
                />

                <Box className="flex items-center flex-wrap gap-1">
                  <IconButton size="small" onClick={(e) => setEmojiAnchorEl(e.currentTarget)}>
                    <EmojiEmotions fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    onClick={(e) => setFeelingAnchorEl(e.currentTarget)}
                    sx={{ color: activeFeeling ? 'primary.main' : undefined }}
                  >
                    <Mood fontSize="small" />
                  </IconButton>
                  {!hasImages && (
                    <IconButton
                      size="small"
                      onClick={() => setShowBgStrip((v) => !v)}
                      sx={{ color: activeBackground ? 'primary.main' : undefined }}
                    >
                      <Palette fontSize="small" />
                    </IconButton>
                  )}
                  <IconButton
                    size="small"
                    onClick={() => fileInputRef.current?.click()}
                    sx={{ color: hasImages ? 'primary.main' : undefined }}
                  >
                    <ImageIcon fontSize="small" />
                  </IconButton>
                  {activeFeeling && (
                    <Chip
                      size="small"
                      label={t('feelingStatus', { emoji: activeFeeling.emoji, feeling: t(`feeling.${activeFeeling.key}`) })}
                      onDelete={() => setFeelingKey(null)}
                      sx={{ ml: 0.5 }}
                    />
                  )}
                </Box>

                {!hasImages && (
                  <Collapse in={showBgStrip} timeout={150} unmountOnExit>
                    <Box className="flex items-center gap-1.5" sx={{ overflowX: 'auto', py: 0.5 }}>
                      <Box
                        onClick={() => setBackgroundKey(null)}
                        sx={{
                          width: 30,
                          height: 30,
                          borderRadius: '50%',
                          flexShrink: 0,
                          cursor: 'pointer',
                          bgcolor: 'background.paper',
                          border: '2px solid',
                          borderColor: !backgroundKey ? 'primary.main' : 'divider',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <Typography sx={{ fontSize: 11, fontWeight: 800 }}>Aa</Typography>
                      </Box>
                      {POST_BACKGROUNDS.map((bg) => (
                        <Box
                          key={bg.key}
                          onClick={() => setBackgroundKey(bg.key)}
                          sx={{
                            width: 30,
                            height: 30,
                            borderRadius: '50%',
                            flexShrink: 0,
                            cursor: 'pointer',
                            background: bg.gradient,
                            border: '2px solid',
                            borderColor: backgroundKey === bg.key ? 'primary.main' : 'transparent',
                            transition: 'transform 120ms ease',
                            '&:hover': { transform: 'scale(1.15)' },
                          }}
                        />
                      ))}
                    </Box>
                  </Collapse>
                )}

                {hasImages && (
                  <Box className="flex flex-col gap-2">
                    <LinearProgress
                      variant="determinate"
                      value={avgProgress}
                      sx={{ borderRadius: 99, height: 6 }}
                    />
                    <Typography variant="caption" color="text.secondary">
                      {t('reorderImagesHint')}
                    </Typography>
                    <Grid container spacing={1}>
                      {pendingUploads.map((p, index) => (
                        <Grid key={p.key} size={{ xs: 4, sm: 3 }}>
                          <Box
                            draggable
                            onDragStart={() => {
                              dragKeyRef.current = p.key;
                              setDraggingKey(p.key);
                            }}
                            onDragEnd={() => {
                              dragKeyRef.current = null;
                              setDraggingKey(null);
                            }}
                            onDragOver={(e) => e.preventDefault()}
                            onDrop={() => handleImageDrop(p.key)}
                            sx={{
                              position: 'relative',
                              aspectRatio: '1 / 1',
                              borderRadius: 1.5,
                              overflow: 'hidden',
                              border: '1px solid',
                              borderColor: p.error ? 'error.main' : 'divider',
                              bgcolor: 'action.hover',
                              cursor: 'grab',
                              opacity: draggingKey === p.key ? 0.35 : 1,
                              transition: 'opacity 120ms ease, transform 120ms ease',
                              '&:hover': { transform: 'scale(1.02)' },
                            }}
                          >
                            <Box
                              component="img"
                              src={p.previewUrl}
                              alt=""
                              draggable={false}
                              sx={{
                                width: '100%',
                                height: '100%',
                                objectFit: 'cover',
                                opacity: p.progress < 100 ? 0.6 : 1,
                              }}
                            />
                            {index === 0 && (
                              <Chip
                                label={t('coverImage')}
                                size="small"
                                sx={{
                                  position: 'absolute',
                                  top: 2,
                                  left: 2,
                                  height: 18,
                                  fontSize: 10,
                                  bgcolor: 'rgba(0,0,0,0.55)',
                                  color: '#fff',
                                  '& .MuiChip-label': { px: 0.75 },
                                }}
                              />
                            )}
                            {p.progress < 100 && !p.error && (
                              <Box
                                sx={{
                                  position: 'absolute',
                                  bottom: 0,
                                  left: 0,
                                  right: 0,
                                  height: 3,
                                  bgcolor: 'primary.main',
                                  width: `${p.progress}%`,
                                  transition: 'width 120ms',
                                }}
                              />
                            )}
                            {p.error && (
                              <Box
                                sx={{
                                  position: 'absolute',
                                  bottom: 0,
                                  left: 0,
                                  right: 0,
                                  bgcolor: 'error.main',
                                  color: '#fff',
                                  px: 0.75,
                                  py: 0.25,
                                  fontSize: 10,
                                }}
                              >
                                {t('errorLabel')}
                              </Box>
                            )}
                            <IconButton
                              size="small"
                              onClick={() => handleRemoveImage(p.key)}
                              sx={{
                                position: 'absolute',
                                top: 2,
                                right: 2,
                                bgcolor: 'rgba(0,0,0,0.5)',
                                color: '#fff',
                                p: 0.25,
                                '&:hover': { bgcolor: 'rgba(0,0,0,0.7)' },
                              }}
                            >
                              <Close sx={{ fontSize: 14 }} />
                            </IconButton>
                          </Box>
                        </Grid>
                      ))}
                    </Grid>
                  </Box>
                )}

                <Box className="mt-1 flex justify-end gap-2">
                  {(title || content || pendingUploads.length > 0) && (
                    <Button size="small" color="inherit" onClick={handleCancel} disabled={posting}>
                      {t('cancel')}
                    </Button>
                  )}
                  <Button
                    variant="contained"
                    size="small"
                    endIcon={<Send fontSize="small" />}
                    disabled={!canSubmit || posting}
                    onClick={handleSubmit}
                  >
                    {posting ? t('posting') : t('postSubmit')}
                  </Button>
                </Box>
              </Box>
            </Collapse>
          </Box>
        </Box>
      </CardContent>

      <Popover
        open={Boolean(emojiAnchorEl)}
        anchorEl={emojiAnchorEl}
        onClose={() => setEmojiAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
      >
        <EmojiPicker
          onEmojiClick={handleEmojiClick}
          theme={themeMode === 'dark' ? Theme.DARK : Theme.LIGHT}
          lazyLoadEmojis
        />
      </Popover>

      <Popover
        open={Boolean(feelingAnchorEl)}
        anchorEl={feelingAnchorEl}
        onClose={() => setFeelingAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        transformOrigin={{ vertical: 'top', horizontal: 'left' }}
      >
        <Box sx={{ p: 1.5, width: 300 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
            {t('howAreYouFeeling')}
          </Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
            {POST_FEELINGS.map((f) => (
              <Chip
                key={f.key}
                label={`${f.emoji} ${t(`feeling.${f.key}`)}`}
                size="small"
                variant={feelingKey === f.key ? 'filled' : 'outlined'}
                color={feelingKey === f.key ? 'primary' : 'default'}
                onClick={() => {
                  setFeelingKey((prev) => (prev === f.key ? null : f.key));
                  setFeelingAnchorEl(null);
                }}
                sx={{ cursor: 'pointer' }}
              />
            ))}
          </Box>
        </Box>
      </Popover>
    </Card>
  );
};

export default PostComposer;
