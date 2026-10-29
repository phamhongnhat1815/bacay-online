package com.game3cay.gui;

import com.game3cay.shared.model.Card;
import com.game3cay.shared.game.HandEvaluator;
import java.awt.Dimension;
import java.awt.Frame;
import java.util.List;
import javax.swing.JDialog;

public final class CardSqueezeDialog extends JDialog {
    private final CardSqueezePanel panel;

    CardSqueezeDialog(Frame owner, List<Card> cards, HandRevealState reveals, Runnable onChanged) {
        super(owner, "Nặn bài của bạn", false);
        panel = new CardSqueezePanel(cards, reveals, onChanged, this::dispose);
        setContentPane(panel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(650, 510));
        setSize(700, 550);
        setLocationRelativeTo(owner);
    }

    void context(String text, boolean urgent) { panel.context(text, urgent); }

    public static String evaluateHandTitle(List<Card> cards) {
        if (cards == null || cards.size() != 3) return "";
        var hand = new HandEvaluator().evaluate(cards);
        return switch (hand.handType()) {
            case SAP -> "SÁP " + cards.getFirst().rank().displayName();
            case LIENG -> "LIÊNG";
            case BO_DOI -> "BỒ ĐỘI (3 TÂY)";
            case DIEM -> hand.score() + " ĐIỂM";
        };
    }
}