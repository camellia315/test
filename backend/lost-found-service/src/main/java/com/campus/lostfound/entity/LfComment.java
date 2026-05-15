package com.campus.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("lf_comment")
public class LfComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long lostFoundId;
    private Long userId;
    private String content;
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private String commenterUserId;
    @TableField(exist = false)
    private String commenterUserNo;
    @TableField(exist = false)
    private String commenterUsername;
    @TableField(exist = false)
    private String commenterAvatarUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLostFoundId() {
        return lostFoundId;
    }

    public void setLostFoundId(Long lostFoundId) {
        this.lostFoundId = lostFoundId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCommenterUserId() {
        return commenterUserId;
    }

    public void setCommenterUserId(String commenterUserId) {
        this.commenterUserId = commenterUserId;
    }

    public String getCommenterUserNo() {
        return commenterUserNo;
    }

    public void setCommenterUserNo(String commenterUserNo) {
        this.commenterUserNo = commenterUserNo;
    }

    public String getCommenterUsername() {
        return commenterUsername;
    }

    public void setCommenterUsername(String commenterUsername) {
        this.commenterUsername = commenterUsername;
    }

    public String getCommenterAvatarUrl() {
        return commenterAvatarUrl;
    }

    public void setCommenterAvatarUrl(String commenterAvatarUrl) {
        this.commenterAvatarUrl = commenterAvatarUrl;
    }
}
