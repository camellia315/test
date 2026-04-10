package com.campus.stats.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StartupHealthCheckService {

    private final RestTemplate statsRestTemplate;

    public StartupHealthCheckService(RestTemplate statsRestTemplate) {
        this.statsRestTemplate = statsRestTemplate;
    }

    public Map<String, Object> check(HttpServletRequest request) {
        Map<String, Object> gatewayStatus = buildGatewayStatus(request);
        Map<String, Object> statsStatus = buildStatsStatus();
        Map<String, Object> userStatus = probeUserService();

        boolean overallUp = isUp(gatewayStatus) && isUp(statsStatus) && isUp(userStatus);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overallUp", overallUp);
        result.put("gateway", gatewayStatus);
        result.put("stats", statsStatus);
        result.put("user", userStatus);
        result.put("checkedAt", LocalDateTime.now().toString());
        return result;
    }

    private Map<String, Object> buildGatewayStatus(HttpServletRequest request) {
        Map<String, Object> gateway = new LinkedHashMap<>();
        gateway.put("service", "gateway");
        gateway.put("up", true);
        gateway.put("message", "health endpoint reachable via gateway route");
        gateway.put("host", request.getHeader("Host"));
        gateway.put("forwardedHost", valueOrEmpty(request.getHeader("X-Forwarded-Host")));
        gateway.put("forwardedPort", valueOrEmpty(request.getHeader("X-Forwarded-Port")));
        return gateway;
    }

    private Map<String, Object> buildStatsStatus() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("service", "stats-service");
        stats.put("up", true);
        stats.put("message", "stats-service is running");
        return stats;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> probeUserService() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("service", "user-service");
        try {
            ResponseEntity<Map> response = statsRestTemplate.getForEntity(
                    "http://user-service/api/users/health/startup",
                    Map.class
            );

            boolean httpOk = response.getStatusCode().is2xxSuccessful();
            Map<String, Object> body = response.getBody() == null ? Map.of() : response.getBody();
            int code = parseInt(body.get("code"), -1);
            boolean payloadOk = code == 0;

            user.put("up", httpOk && payloadOk);
            user.put("httpStatus", response.getStatusCodeValue());
            user.put("message", httpOk && payloadOk ? "user-service is reachable" : "user-service response is abnormal");
            user.put("responseCode", code);
        } catch (Exception ex) {
            user.put("up", false);
            user.put("httpStatus", 0);
            user.put("message", ex.getClass().getSimpleName() + ": " + valueOrEmpty(ex.getMessage()));
            user.put("responseCode", -1);
        }
        return user;
    }

    private boolean isUp(Map<String, Object> serviceStatus) {
        Object raw = serviceStatus.get("up");
        if (raw instanceof Boolean value) {
            return value;
        }
        return false;
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}

