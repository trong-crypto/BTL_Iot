package org.example.back_end_iot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authorization.AuthorizationManager;
import org.example.back_end_iot.security.JwtService;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import java.time.Instant;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtService tokens;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> expiryTasks = new ConcurrentHashMap<>();

    public WebSocketConfig(JwtService tokens) { this.tokens = tokens; }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                expireAt(session.getId(), Instant.now().plusSeconds(10));
                super.afterConnectionEstablished(session);
            }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.remove(session.getId());
                var task = expiryTasks.remove(session.getId());
                if (task != null) task.cancel(false);
                super.afterConnectionClosed(session, status);
            }
        });
    }

    private void expireAt(String sessionId, Instant expiry) {
        var previous = expiryTasks.remove(sessionId);
        if (previous != null) previous.cancel(false);
        var task = stompScheduler().schedule(() -> {
            var session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try { session.close(CloseStatus.POLICY_VIOLATION); }
                catch (IOException ignored) { /* Transport cleanup handles disconnected clients. */ }
            }
        }, expiry);
        expiryTasks.put(sessionId, task);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    if (header == null || !header.startsWith("Bearer "))
                        throw new BadCredentialsException("JWT is required for STOMP CONNECT");
                    try {
                        var jwt = tokens.decoder().decode(header.substring(7));
                        accessor.setUser(new JwtAuthenticationToken(jwt, java.util.List.of(), jwt.getSubject()));
                        expireAt(accessor.getSessionId(), jwt.getExpiresAt());
                    } catch (JwtException ex) { throw new BadCredentialsException("Invalid or expired JWT"); }
                } else if (!StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    if (!(accessor.getUser() instanceof JwtAuthenticationToken auth) ||
                            !auth.getToken().getExpiresAt().isAfter(Instant.now()))
                        throw new BadCredentialsException("Invalid or expired JWT");
                }
                return message;
            }
        }, new SecurityContextChannelInterceptor(),
                new AuthorizationChannelInterceptor(messageAuthorizationManager()));
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000}).setTaskScheduler(stompScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
    }

    @Bean
    public ThreadPoolTaskScheduler stompScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        return scheduler;
    }

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager() {
        var messages = MessageMatcherDelegatingAuthorizationManager.builder();
        return messages.simpTypeMatchers(SimpMessageType.DISCONNECT).permitAll()
                .nullDestMatcher().authenticated()
                .simpSubscribeDestMatchers("/topic/sensor-data", "/topic/devices", "/topic/availability",
                        "/user/queue/commands").authenticated()
                .simpTypeMatchers(SimpMessageType.MESSAGE, SimpMessageType.SUBSCRIBE).denyAll()
                .anyMessage().denyAll().build();
    }
}
