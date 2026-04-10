package com.campus.market.controller;

import com.campus.common.api.ApiResponse;
import com.campus.market.dto.ChatReadRequest;
import com.campus.market.dto.ChatSendRequest;
import com.campus.market.entity.ChatMessageEntity;
import com.campus.market.entity.ChatSession;
import com.campus.market.service.ChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/chat")
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void sendViaWebSocket(@Payload ChatSendRequest request) {
        ChatMessageEntity message = chatService.sendMessage(request);
        pushMessage(message);
    }

    @PostMapping("/messages")
    public ApiResponse<ChatMessageEntity> send(@RequestBody ChatSendRequest request) {
        ChatMessageEntity message = chatService.sendMessage(request);
        pushMessage(message);
        return ApiResponse.success(message);
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> sessions(@RequestParam Long userId) {
        return ApiResponse.success(chatService.listSessions(userId));
    }

    @GetMapping("/messages")
    public ApiResponse<Map<String, Object>> messages(@RequestParam Long userId,
                                                     @RequestParam Long otherUserId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(chatService.pageMessages(userId, otherUserId, page, size));
    }

    @PostMapping("/read")
    public ApiResponse<Map<String, Object>> read(@RequestBody ChatReadRequest request) {
        return ApiResponse.success(chatService.markRead(request));
    }

    @GetMapping("/unread")
    public ApiResponse<Map<String, Object>> unread(@RequestParam Long userId) {
        return ApiResponse.success(chatService.unreadSummary(userId));
    }

    private void pushMessage(ChatMessageEntity message) {
        String targetQueue = "/queue/market.chat." + message.getToUserId();
        String selfQueue = "/queue/market.chat." + message.getFromUserId();
        messagingTemplate.convertAndSend(targetQueue, message);
        messagingTemplate.convertAndSend(selfQueue, message);
    }
}

