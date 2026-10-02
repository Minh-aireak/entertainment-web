import { describe, expect, it } from 'vitest';
import type { ChatMessage } from '../models';
import { mergeLatestMessages, setMessages, store } from './index';

let conversationCounter = 0;
const nextConversationId = () => `conversation-${++conversationCounter}`;

const message = (conversationId: string, seq: number, overrides: Partial<ChatMessage> = {}): ChatMessage => ({
  id: `${conversationId}-m${seq}`,
  conversationId,
  senderId: 'user-1',
  messageType: 'TEXT',
  content: `message ${seq}`,
  seq,
  createdDate: new Date(seq * 1000).toISOString(),
  messageStatus: 'SENT',
  ...overrides,
});

const cachedMessages = (conversationId: string) => store.getState().chat.messages[conversationId];

describe('chat mergeLatestMessages', () => {
  it('slots in messages missed while out of the room without dropping load-more history', () => {
    const conversationId = nextConversationId();
    // seq 1-5 loaded earlier (incl. via "load more"), seq 7 arrived live after rejoining the room.
    const cached = [1, 2, 3, 4, 5, 7].map((seq) => message(conversationId, seq));
    store.dispatch(setMessages({ conversationId, messages: cached }));

    const latestPage = [4, 5, 6, 7, 8].map((seq) => message(conversationId, seq));
    store.dispatch(mergeLatestMessages({ conversationId, messages: latestPage }));

    expect(cachedMessages(conversationId).map((item) => item.seq)).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
  });

  it('slots a missed message that opens the page in before its next neighbour', () => {
    const conversationId = nextConversationId();
    store.dispatch(setMessages({ conversationId, messages: [3, 5].map((seq) => message(conversationId, seq)) }));

    store.dispatch(mergeLatestMessages({ conversationId, messages: [4, 5].map((seq) => message(conversationId, seq)) }));

    expect(cachedMessages(conversationId).map((item) => item.seq)).toEqual([3, 4, 5]);
  });

  it('keeps server order when seq repeats after the chat seed reset totalSeq', () => {
    const conversationId = nextConversationId();
    const beforeReset = [4, 5].map((seq) => message(conversationId, seq, { id: `${conversationId}-before-${seq}` }));
    const afterReset = [4, 5].map((seq) => message(conversationId, seq, { id: `${conversationId}-after-${seq}` }));
    store.dispatch(setMessages({ conversationId, messages: [...beforeReset, afterReset[0]] }));

    store.dispatch(mergeLatestMessages({ conversationId, messages: [beforeReset[1], ...afterReset] }));

    expect(cachedMessages(conversationId).map((item) => item.id))
      .toEqual([...beforeReset, ...afterReset].map((item) => item.id));
  });

  it('keeps the cached copy of an unchanged attachment so its URL does not change', () => {
    const conversationId = nextConversationId();
    const image = message(conversationId, 1, {
      messageType: 'IMAGE',
      content: 'Sent an image',
      attachmentFileUrl: 'https://b2.example/image.png?X-Amz-Signature=first',
    });
    store.dispatch(setMessages({ conversationId, messages: [image] }));

    const refetched = { ...image, attachmentFileUrl: 'https://b2.example/image.png?X-Amz-Signature=second' };
    store.dispatch(mergeLatestMessages({ conversationId, messages: [refetched] }));

    expect(cachedMessages(conversationId)[0].attachmentFileUrl).toBe(image.attachmentFileUrl);
  });

  it('takes the fetched copy of a message edited or recalled meanwhile', () => {
    const conversationId = nextConversationId();
    store.dispatch(setMessages({
      conversationId,
      messages: [message(conversationId, 1), message(conversationId, 2)],
    }));

    store.dispatch(mergeLatestMessages({
      conversationId,
      messages: [
        message(conversationId, 1, { content: 'message 1 (edited)' }),
        message(conversationId, 2, { messageType: 'DELETED_FOR_EVERYONE' }),
      ],
    }));

    const [edited, recalled] = cachedMessages(conversationId);
    expect(edited.content).toBe('message 1 (edited)');
    expect(recalled.messageType).toBe('DELETED_FOR_EVERYONE');
  });
});
