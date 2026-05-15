package com.campus.user.dto;

public class UpdateProfileRequest {
    private String userId;
    private String email;
    private String phone;
    private String avatarUrl;
    private String bio;
    private String homepageCover;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getHomepageCover() {
        return homepageCover;
    }

    public void setHomepageCover(String homepageCover) {
        this.homepageCover = homepageCover;
    }
}
