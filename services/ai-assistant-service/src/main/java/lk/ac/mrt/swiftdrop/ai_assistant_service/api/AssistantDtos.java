package lk.ac.mrt.swiftdrop.ai_assistant_service.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AssistantDtos {
    private AssistantDtos() {}

    public record ChatRequest(
        @NotBlank @Size(max = 2000) String message,
        UUID conversationId
    ) {}

    public record ConversationMessage(String role, String content) {}

    public record ConversationView(UUID conversationId, List<ConversationMessage> messages) {}

    public record ToolEvent(String name, Object arguments) {}
}
