package com.game3cay.shared.model;

public record PageRequest(int page, int pageSize) implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
}
