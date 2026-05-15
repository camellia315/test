package com.campus.lostfound.dto;

public class LostFoundAuditRequest {
    private Long auditorId;
    private String auditorRole;
    private Integer status;
    private String reason;

    public Long getAuditorId() {
        return auditorId;
    }

    public void setAuditorId(Long auditorId) {
        this.auditorId = auditorId;
    }

    public String getAuditorRole() {
        return auditorRole;
    }

    public void setAuditorRole(String auditorRole) {
        this.auditorRole = auditorRole;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
