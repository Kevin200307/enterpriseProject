package lk.ac.mrt.swiftdrop.ai_assistant_service.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import lk.ac.mrt.swiftdrop.ai_assistant_service.service.AssistantChatService;
import lk.ac.mrt.swiftdrop.ai_assistant_service.service.AssistantConversationStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class AssistantControllerTest {
    private final AssistantChatService chat = mock(AssistantChatService.class);
    private final AssistantConversationStore conversations = new AssistantConversationStore();
    private final AssistantController controller = new AssistantController(chat, conversations, true);

    @Test
    void streamsForTheAuthenticatedUserAndReturnsEventStream() {
        String ownerId = UUID.randomUUID().toString();
        UUID conversationId = UUID.randomUUID();
        Jwt jwt = jwt(ownerId);

        var response = controller.stream(new AssistantDtos.ChatRequest(" hello ", conversationId), jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).hasToString("text/event-stream");
        verify(chat).stream(org.mockito.ArgumentMatchers.eq(ownerId),
            org.mockito.ArgumentMatchers.eq("test"), org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("hello"),
            org.mockito.ArgumentMatchers.eq(conversationId),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void conversationLookupHidesConversationsOwnedByAnotherUser() {
        UUID id = conversations.create("user-a");

        assertThatThrownBy(() -> controller.conversation(id, jwt("user-b")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void disabledAssistantReturnsServiceUnavailable() {
        AssistantController disabled = new AssistantController(chat, conversations, false);

        assertThatThrownBy(() -> disabled.stream(new AssistantDtos.ChatRequest("hello", null), jwt("user-a")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(chat);
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("test")
            .header("alg", "none")
            .claim("sub", subject)
            .build();
    }
}
