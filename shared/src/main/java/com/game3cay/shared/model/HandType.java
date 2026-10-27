package com.game3cay.shared.model;

public enum HandType {
  SAP(4, "Sáp"),

  LIENG(3, "Liêng"),

  BO_DOI(2, "Bồ đội"),

  DIEM(1, "Điểm");

  private final int strength;
  private final String displayName;

  HandType(int strength, String displayName) {
    this.strength = strength;
    this.displayName = displayName;
  }

  public int strength() {
    return strength;
  }

  public String displayName() {
    return displayName;
  }
}
