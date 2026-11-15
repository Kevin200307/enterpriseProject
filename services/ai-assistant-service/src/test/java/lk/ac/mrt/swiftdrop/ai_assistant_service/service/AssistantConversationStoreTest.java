package lk.ac.mrt.swiftdrop.ai_assistant_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssistantConversationStoreTest {
    private final AssistantConversationStore conversations = new AssistantConversationStore();

    @Test
    void conversationIsPrivateToItsOwnerAndCanBeDeleted() {
        UUID id = conversations.create("user-a");
        conversations.addUserMessage(id, "user-a", "hello");

        assertThat(conversations.messages(id, "user-a")).isPresent();
        assertThat(conversations.messages(id, "user-b")).isEmpty();
        assertThat(conversations.delete(id, "user-b")).isFalse();
        assertThat(conversations.delete(id, "user-a")).isTrue();
        assertThat(conversations.messages(id, "user-a")).isEmpty();
    }

    @Test
    void retainsOnlyTheLatestTwentyMessages() {
        UUID id = conversations.create("user-a");
        for (int i = 0; i < 12; i++) {
            conversations.addUserMessage(id, "user-a", "question-" + i);
            conversations.addAssistantMessage(id, "user-a", "answer-" + i);
        }

        var messages = conversations.messages(id, "user-a").orElseThrow();
        assertThat(messages).hasSize(20);
        assertThat(messages.getFirst().content()).isEqualTo("question-2");
        assertThat(messages.getLast().content()).isEqualTo("answer-11");
    }
}
