package com.inkpress.common;

/**
 * 常量定义
 */
public final class Constants {

    private Constants() {
    }

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    /** 文章状态 */
    public static final String ARTICLE_DRAFT = "DRAFT";
    public static final String ARTICLE_PUBLISHED = "PUBLISHED";

    /** 评论状态 */
    public static final String COMMENT_PENDING = "PENDING";
    public static final String COMMENT_APPROVED = "APPROVED";
    public static final String COMMENT_REJECTED = "REJECTED";

    /** Redis Key 前缀 */
    public static final String KEY_ARTICLE_VIEWS = "inkpress:views:";
    public static final String KEY_ARTICLE_VIEWS_DIRTY = "inkpress:views:dirty";
    public static final String KEY_ARTICLE_LIKED = "inkpress:liked:";
    public static final String KEY_HOT_ARTICLES = "inkpress:hot:articles";
}
