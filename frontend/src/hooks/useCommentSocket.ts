import { useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useWebSocket } from '../contexts/WebSocketContext';
import { addComment, decrementReplyCount, incrementReplyCount, patchComment, removeComment, replaceComment } from '../store';
import type { AppDispatch, RootState } from '../store';
import type { CommentReactionChangedEvent, CommentResponse } from '../api/commentService';

const COMMENT_CREATED_EVENT = 'comment:created';
const COMMENT_UPDATED_EVENT = 'comment:updated';
const COMMENT_DELETED_EVENT = 'comment:deleted';
const COMMENT_REACTION_EVENT = 'comment:reaction';

export interface CommentSocketCallbacks {
  onCommentCreated?: (comment: CommentResponse) => void;
  onCommentUpdated?: (comment: CommentResponse) => void;
  onCommentDeleted?: (comment: CommentResponse) => void;
}

/**
 * Joins/leaves the realtime room for a given sourceId (postId/filmId) on the WebSocket
 * connection already established for chat (WebSocketContext), and keeps the Redux comment
 * slice in sync with "comment:created"/"comment:updated"/"comment:deleted" broadcasts for
 * that post - covering both its top-level comments and every reply thread under it, since
 * replies are just comments with parentId set and ride the exact same room/event pipeline.
 *
 * The third argument accepts either a legacy single `onCommentCreated` callback (backward
 * compatible with existing call sites) or a `CommentSocketCallbacks` object exposing all
 * three lifecycle hooks.
 *
 * The server's room engine only recognizes the generic "join-room"/"leave-room" frame types
 * (roomId = sourceId here) - the same ones chat uses for conversationId. Sending/editing/
 * deleting a comment is still done via REST; every viewer (including the actor) only mutates
 * the list once the corresponding broadcast arrives here, so there is no local optimistic
 * insert/replace/remove for those three actions anywhere else in the comment UI.
 */
export const useCommentSocket = (
  sourceId: string,
  enabled: boolean,
  callbacks: CommentSocketCallbacks | ((comment: CommentResponse) => void),
) => {
  const { send, subscribe, isConnected } = useWebSocket();
  const dispatch = useDispatch<AppDispatch>();
  const currentUserId = useSelector((state: RootState) => state.auth.user?.id);

  const normalized: CommentSocketCallbacks =
    typeof callbacks === 'function' ? { onCommentCreated: callbacks } : callbacks;

  const onCreatedRef = useRef(normalized.onCommentCreated);
  const onUpdatedRef = useRef(normalized.onCommentUpdated);
  const onDeletedRef = useRef(normalized.onCommentDeleted);

  useEffect(() => {
    onCreatedRef.current = normalized.onCommentCreated;
    onUpdatedRef.current = normalized.onCommentUpdated;
    onDeletedRef.current = normalized.onCommentDeleted;
  }, [normalized.onCommentCreated, normalized.onCommentDeleted, normalized.onCommentUpdated]);

  useEffect(() => {
    if (!enabled || !sourceId || !isConnected) return;

    send({ type: 'join-room', roomId: sourceId });

    return () => {
      send({ type: 'leave-room', roomId: sourceId });
    };
  }, [enabled, sourceId, isConnected, send]);

  useEffect(() => {
    if (!enabled || !sourceId) return;

    const unsubscribers = [
      subscribe(COMMENT_CREATED_EVENT, (data: CommentResponse) => {
        if (data?.sourceId !== sourceId) return;
        if (data.parentId) {
          dispatch(addComment({ groupId: data.parentId, comment: data }));
          dispatch(incrementReplyCount({ groupId: sourceId, commentId: data.parentId }));
        } else {
          dispatch(addComment({ groupId: sourceId, comment: data }));
        }
        onCreatedRef.current?.(data);
      }),
      subscribe(COMMENT_UPDATED_EVENT, (data: CommentResponse) => {
        if (data?.sourceId !== sourceId) return;
        const groupId = data.parentId || sourceId;
        dispatch(replaceComment({ groupId, comment: data }));
        onUpdatedRef.current?.(data);
      }),
      subscribe(COMMENT_DELETED_EVENT, (data: CommentResponse) => {
        if (data?.sourceId !== sourceId) return;
        if (data.parentId) {
          dispatch(removeComment({ groupId: data.parentId, commentId: data.id }));
          dispatch(decrementReplyCount({ groupId: sourceId, commentId: data.parentId }));
        } else {
          dispatch(removeComment({ groupId: sourceId, commentId: data.id }));
        }
        onDeletedRef.current?.(data);
      }),
      subscribe(COMMENT_REACTION_EVENT, (data: CommentReactionChangedEvent) => {
        if (data?.sourceId !== sourceId) return;
        const patch = {
          likeCount: data.likeCount,
          loveCount: data.loveCount,
          ...(data.actorUserId === currentUserId ? { myReaction: data.myReaction } : {}),
        };
        dispatch(patchComment({
          groupId: data.parentId || sourceId,
          commentId: data.commentId,
          patch,
        }));
      }),
    ];

    return () => unsubscribers.forEach((unsubscribe) => unsubscribe());
  }, [enabled, sourceId, subscribe, dispatch, currentUserId]);
};
