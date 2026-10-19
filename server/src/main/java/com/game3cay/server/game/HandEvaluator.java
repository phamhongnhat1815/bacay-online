package com.game3cay.server.game;

import com.game3cay.shared.model.*;
import java.util.*;

/** Luật của nhóm: Sáp > Liêng > Bồ đội > Điểm; A23 thấp nhất, QKA cao nhất. */
public final class HandEvaluator implements Comparator<Hand> {
  public Hand evaluate(List<Card> cards) {
    if (cards == null || cards.size() != 3 || new HashSet<>(cards).size() != 3)
      throw new IllegalArgumentException("Cần 3 lá khác nhau");
    int[] ranks = cards.stream().mapToInt(c -> c.rank().rankValue()).sorted().toArray();
    HandType type =
        ranks[0] == ranks[2]
            ? HandType.SAP
            : straightHigh(ranks) > 0
                ? HandType.LIENG
                : cards.stream().allMatch(c -> c.rank().isFaceCard())
                    ? HandType.BO_DOI
                    : HandType.DIEM;
    return new Hand(cards, type, cards.stream().mapToInt(Card::pointValue).sum() % 10);
  }

  private int straightHigh(int[] a) {
    if (a[0] == 2 && a[1] == 3 && a[2] == 14) return 3;
    return a[1] == a[0] + 1 && a[2] == a[1] + 1 ? a[2] : 0;
  }

  private int value(Hand h) {
    int[] a = h.cards().stream().mapToInt(c -> c.rank().rankValue()).sorted().toArray();
    return switch (h.handType()) {
      case SAP -> a[0];
      case LIENG -> straightHigh(a);
      case DIEM -> h.score();
      case BO_DOI -> 0;
    };
  }

  @Override
  public int compare(Hand a, Hand b) {
    int c = Integer.compare(a.handType().strength(), b.handType().strength());
    if (c != 0) return c;
    c = Integer.compare(value(a), value(b));
    if (c != 0) return c;
    // So lần lượt các lá Rô, Cơ, Bích, Chuồn; trong cùng chất xét A cao.
    var order =
        Comparator.comparingInt((Card x) -> x.suit().compareValue())
            .thenComparingInt(x -> x.rank().rankValue())
            .reversed();
    var ac = a.cards().stream().sorted(order).toList();
    var bc = b.cards().stream().sorted(order).toList();
    for (int i = 0; i < 3; i++) {
      c = Integer.compare(ac.get(i).suit().compareValue(), bc.get(i).suit().compareValue());
      if (c == 0) c = Integer.compare(ac.get(i).rank().rankValue(), bc.get(i).rank().rankValue());
      if (c != 0) return c;
    }
    return 0;
  }
}
