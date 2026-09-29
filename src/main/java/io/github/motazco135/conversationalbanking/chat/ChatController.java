package io.github.motazco135.conversationalbanking.chat;

import io.github.motazco135.conversationalbanking.chat.dto.BankChatRequest;
import io.github.motazco135.conversationalbanking.chat.dto.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/bank/chat")
@Tag(name = "Bank agent", description = "Conversational Banking Agent")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "Chat with Bank agent")
    @PostMapping("/messages")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody BankChatRequest request) {
        ChatResponse response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
    }
}
