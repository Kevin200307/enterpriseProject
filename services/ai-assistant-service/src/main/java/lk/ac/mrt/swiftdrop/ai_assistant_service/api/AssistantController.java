package lk.ac.mrt.swiftdrop.ai_assistant_service.api;

import java.util.UUID;
import lk.ac.mrt.swiftdrop.ai_assistant_service.api.AssistantDtos.ChatRequest;
import lk.ac.mrt.swiftdrop.ai_assistant_service.api.AssistantDtos.ConversationView;
import lk.ac.mrt.swiftdrop.ai_assistant_service.service.AssistantChatService;
import lk.ac.mrt.swiftdrop.ai_assistant_service.service.AssistantConversationStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private final AssistantChatService chat;
    private final AssistantConversationStore conversations;
    private final boolean enabled;

    public AssistantController(
        AssistantChatService chat,
        AssistantConversationStore conversations,
        @Value("${assistant.enabled:true}") boolean enabled
    ) {
        this.chat = chat;
        this.conversations = conversations;
        this.enabled = enabled;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
        @Valid @RequestBody ChatRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        requireEnabled();
        SseEmitter emitter = new SseEmitter(120_000L);
        chat.stream(
            jwt.getSubject(),
            jwt.getTokenValue(),
            jwt.getClaimAsStringList("roles"),
            request.message().trim(),
            request.conversationId(),
            emitter
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONNECTION, "keep-alive")
            .header("X-Accel-Buffering", "no")
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(emitter);
    }

    @GetMapping("/conversations/{id}")
    public ConversationView conversation(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireEnabled();
        return conversations.messages(id, jwt.getSubject())
            .map(messages -> new ConversationView(id, messages))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        requireEnabled();
        if (!conversations.delete(id, jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
    }

    private void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Assistant is disabled");
    }
}
