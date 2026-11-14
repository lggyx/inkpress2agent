-- V24：平台授权状态表（B 档授权三分法的站内可见性）
-- distributor CLI `auth status` 检测后回报；管理端展示各平台登录态/最后检查时间
CREATE TABLE IF NOT EXISTS platform_auth_status (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    platform     VARCHAR(32)  NOT NULL UNIQUE COMMENT '平台标识（juejin/csdn/zhihu/wechat…）',
    auth_type    VARCHAR(16)  NOT NULL DEFAULT 'browser' COMMENT 'browser/qrcode/user_handoff',
    logged_in    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '当前是否已授权',
    note         VARCHAR(255) NULL COMMENT '最近一次检查的说明（Cookie 失效等）',
    device_name  VARCHAR(64)  NULL COMMENT '回报设备名',
    last_checked_at DATETIME NULL,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='平台授权状态（distributor 回报）';
