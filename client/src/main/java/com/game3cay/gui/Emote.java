package com.game3cay.gui;

/**
 * Danh mục biểu cảm / Sticker tương tác giữa các người chơi trên bàn.
 */
public enum Emote {
    LAUGH("laugh", "😀", "Cười sảng khoái"),
    CRY("cry", "😭", "Khóc ròng"),
    ANGRY("angry", "😡", "Cay cú"),
    COOL("cool", "😎", "Đại gia"),
    MONEY("money", "💸", "Tung tiền"),
    CHICKEN("chicken", "🐔", "Gà con"),
    FIRE("fire", "🔥", "Cháy quá"),
    THUMBS_UP("thumbsup", "👍", "Tuyệt vời");

    private final String tag;
    private final String emoji;
    private final String description;

    Emote(String tag, String emoji, String description) {
        this.tag = tag;
        this.emoji = emoji;
        this.description = description;
    }

    public String tag() {
        return tag;
    }

    public String emoji() {
        return emoji;
    }

    public String description() {
        return description;
    }

    public String toChatText() {
        return "[EMOTE:" + tag + "]";
    }

    public static Emote fromTag(String tag) {
        if (tag == null) {
            return null;
        }
        for (Emote e : values()) {
            if (e.tag.equalsIgnoreCase(tag)) {
                return e;
            }
        }
        return null;
    }

    public static Emote fromChatText(String text) {
        if (text == null || !text.startsWith("[EMOTE:") || !text.endsWith("]")) {
            return null;
        }
        String tag = text.substring(7, text.length() - 1);
        return fromTag(tag);
    }
}
