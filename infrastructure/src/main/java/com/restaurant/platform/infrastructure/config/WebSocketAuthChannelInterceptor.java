package com.restaurant.platform.infrastructure.config;

import com.restaurant.platform.application.port.out.BranchStatusPort;
import com.restaurant.platform.domain.model.enums.UserRole;
import com.restaurant.platform.infrastructure.security.CustomUserDetails;
import com.restaurant.platform.infrastructure.security.JwtService;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern STAFF_TOPIC_PATTERN =
            Pattern.compile("^/topic/branch/(\\d+)/(kds|pos|waiter)$");
    private static final Pattern PUBLIC_PICKUP_PATTERN =
            Pattern.compile("^/topic/branch/(\\d+)/pickup$");
    private static final Pattern PUBLIC_ORDER_PATTERN =
            Pattern.compile("^/topic/orders/[A-Za-z0-9_-]+$");

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final BranchStatusPort branchStatusPort;

    public WebSocketAuthChannelInterceptor(JwtService jwtService,
                                           UserDetailsService userDetailsService,
                                           BranchStatusPort branchStatusPort) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.branchStatusPort = branchStatusPort;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String username = jwtService.extractUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    if (!userDetails.isEnabled() || !jwtService.isTokenValid(token, userDetails)) {
                        throw new MessagingException("رمز WebSocket غير صالح أو الحساب موقوف");
                    }
                    var auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    accessor.setUser(auth);
                } catch (Exception ex) {
                    throw new MessagingException("فشل مصادقة WebSocket: " + ex.getMessage());
                }
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination == null) {
                throw new MessagingException("وجهة الاشتراك غير صالحة");
            }

            // قنوات الزبائن وشاشة الاستلام العامة مسموحة للجميع
            if (PUBLIC_PICKUP_PATTERN.matcher(destination).matches()
                    || PUBLIC_ORDER_PATTERN.matcher(destination).matches()) {
                return message;
            }

            // أي قناة أخرى تتطلب مصادقة موظف
            if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)
                    || !(auth.getPrincipal() instanceof CustomUserDetails cud)) {
                throw new MessagingException("غير مصرح بالاشتراك في هذه القناة بدون تسجيل دخول");
            }

            Matcher m = STAFF_TOPIC_PATTERN.matcher(destination);
            if (m.matches()) {
                Long branchId = Long.parseLong(m.group(1));
                String topicType = m.group(2);
                authorizeBranchTopic(cud, branchId, topicType);
            }
        }

        return message;
    }

    private void authorizeBranchTopic(CustomUserDetails cud, Long branchId, String topicType) {
        UserRole role = cud.getRole();
        boolean roleAllowed = switch (topicType) {
            case "kds" -> role == UserRole.KITCHEN_STAFF || role == UserRole.ORG_ADMIN || cud.isOwner();
            case "pos" -> role == UserRole.CASHIER || role == UserRole.ORG_ADMIN || cud.isOwner();
            case "waiter" -> role == UserRole.WAITER || role == UserRole.CASHIER || role == UserRole.ORG_ADMIN || cud.isOwner();
            default -> false;
        };

        if (!roleAllowed && role != UserRole.SUPER_ADMIN) {
            throw new MessagingException("دورك الوظيفي لا يتيح الاشتراك في قناة " + topicType);
        }

        if (role != UserRole.SUPER_ADMIN) {
            Long branchOrgId = branchStatusPort.getOrganizationIdByBranchId(branchId);
            if (!branchOrgId.equals(cud.getOrganizationId())) {
                throw new MessagingException("غير مصرح بالوصول إلى بيانات فرع لمنشأة أخرى");
            }
            if (role != UserRole.ORG_ADMIN && !cud.isOwner()
                    && cud.getBranchId() != null && !cud.getBranchId().equals(branchId)) {
                throw new MessagingException("غير مصرح بالوصول إلى قناة فرع آخر");
            }
        }
    }
}