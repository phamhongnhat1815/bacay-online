package com.game3cay.shared.model;

public record UpdateProfileRequest(String displayName, String email, String avatarUrl)
    implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
}
