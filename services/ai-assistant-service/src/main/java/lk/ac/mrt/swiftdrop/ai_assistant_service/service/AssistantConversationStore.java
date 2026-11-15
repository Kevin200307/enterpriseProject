package lk.ac.mrt.swiftdrop.ai_assistant_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lk.ac.mrt.swiftdrop.ai_assistant_service.api.AssistantDtos.ConversationMessage;
import org.springframework.stereotype.Component;

@Component
public class AssistantConversationStore {
    private static final int MAX_MESSAGES = 20;
    private static final Duration EXPIRATION = Duration.ofHours(24);

    private final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();

    public UUID create(String ownerId) {
        purgeExpired();
        UUID id = UUID.randomUUID();
        conversations.put(id, new Conversation(ownerId));
        return id;
    }

    public Optional<List<ConversationMessage>> messages(UUID id, String ownerId) {
        Conversation conversation = ownedConversation(id, ownerId);
        if (conversation == null) return Optional.empty();
        synchronized (conversation) {
            conversation.lastAccess = Instant.now();
            return Optional.of(List.copyOf(conversation.messages));
        }
    }

    public Optional<List<ConversationMessage>> addUserMessage(UUID id, String ownerId, String content) {
        Conversation conversation = ownedConversation(id, ownerId);
        if (conversation == null) return Optional.empty();
        synchronized (conversation) {
            conversation.messages.add(new ConversationMessage("user", content));
            trim(conversation.messages);
            conversation.lastAccess = Instant.now();
            return Optional.of(List.copyOf(conversation.messages));
        }
    }

    public void addAssistantMessage(UUID id, String ownerId, String content) {
        Conversation conversation = ownedConversation(id, ownerId);
        if (conversation == null) return;
        synchronized (conversation) {
            conversation.messages.add(new ConversationMessage("assistant", content));
            trim(conversation.messages);
            conversation.lastAccess = Instant.now();
        }
    }

    public boolean delete(UUID id, String ownerId) {
        Conversation conversation = ownedConversation(id, ownerId);
        return conversation != null && conversations.remove(id, conversation);
    }

    private Conversation ownedConversation(UUID id, String ownerId) {
        purgeExpired();
        Conversation conversation = conversations.get(id);
        return conversation != null && conversation.ownerId.equals(ownerId) ? conversation : null;
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(EXPIRATION);
        conversations.entrySet().removeIf(entry -> entry.getValue().lastAccess.isBefore(cutoff));
    }

    private static void trim(List<ConversationMessage> messages) {
        while (messages.size() > MAX_MESSAGES) messages.remove(0);
    }

    private static final class Conversation {
        private final String ownerId;
        private final List<ConversationMessage> messages = new ArrayList<>();
        private volatile Instant lastAccess = Instant.now();

        private Conversation(String ownerId) {
            this.ownerId = ownerId;
        }
    }
}
