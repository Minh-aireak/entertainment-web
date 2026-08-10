import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

import { postService } from '../api/postService';
import type { Post } from '../components/Social/types';

// Debounces the network call, not the UI: each click still flips the like state instantly, but
// rapid-fire clicks only settle into a single request once the user pauses - matching the
// backend's own like/unlike debounce so a burst of taps doesn't spam requests or notifications.
const LIKE_DEBOUNCE_MS = 400;

interface PendingLike {
  timer: number;
  baselineLiked: boolean;
  baselineLikeCount: number;
}

export function usePostLikeToggle(posts: Post[], setPosts: Dispatch<SetStateAction<Post[]>>) {
  const { t } = useTranslation();
  const [likingIds, setLikingIds] = useState<Set<string>>(new Set());
  const pendingLikesRef = useRef<Map<string, PendingLike>>(new Map());

  const postsRef = useRef<Post[]>(posts);
  useEffect(() => {
    postsRef.current = posts;
  }, [posts]);

  useEffect(() => {
    const pending = pendingLikesRef.current;
    return () => {
      pending.forEach((entry) => window.clearTimeout(entry.timer));
    };
  }, []);

  const flushLike = useCallback(async (postId: string, baseline: PendingLike) => {
    pendingLikesRef.current.delete(postId);

    const current = postsRef.current.find((p) => p.id === postId);
    if (!current || current.liked === baseline.baselineLiked) {
      // Net no-op for this burst of clicks - state already matches the server, skip the request.
      return;
    }

    setLikingIds((prev) => new Set(prev).add(postId));
    try {
      const res = await postService.toggleLike(postId);
      if (res.data.code === 1000 && res.data.result) {
        const { liked, likeCount } = res.data.result;
        setPosts((prev) => prev.map((p) => (p.id === postId ? { ...p, liked, likeCount } : p)));
      }
    } catch (error) {
      console.error('Failed to toggle like:', error);
      setPosts((prev) =>
        prev.map((p) =>
          p.id === postId
            ? { ...p, liked: baseline.baselineLiked, likeCount: baseline.baselineLikeCount }
            : p
        )
      );
      toast.error(t('likeFailed'));
    } finally {
      setLikingIds((prev) => {
        const next = new Set(prev);
        next.delete(postId);
        return next;
      });
    }
  }, [t, setPosts]);

  const handleToggleLike = useCallback((post: Post) => {
    if (likingIds.has(post.id)) return;

    setPosts((prev) =>
      prev.map((p) =>
        p.id === post.id ? { ...p, liked: !p.liked, likeCount: p.likeCount + (p.liked ? -1 : 1) } : p
      )
    );

    const existing = pendingLikesRef.current.get(post.id);
    if (existing) {
      window.clearTimeout(existing.timer);
    }
    const baseline: PendingLike = existing ?? {
      timer: 0,
      baselineLiked: post.liked,
      baselineLikeCount: post.likeCount,
    };
    baseline.timer = window.setTimeout(() => flushLike(post.id, baseline), LIKE_DEBOUNCE_MS);
    pendingLikesRef.current.set(post.id, baseline);
  }, [likingIds, flushLike, setPosts]);

  return { likingIds, handleToggleLike };
}
