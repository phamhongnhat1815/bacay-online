package com.game3cay.shared.model;

public record ChangePasswordRequest(String currentPassword, String newPassword)
    implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
}
