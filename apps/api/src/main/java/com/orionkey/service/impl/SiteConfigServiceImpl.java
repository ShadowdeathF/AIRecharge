package com.orionkey.service.impl;

import com.orionkey.entity.SiteConfig;
import com.orionkey.constant.ErrorCode;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.SiteConfigRepository;
import com.orionkey.service.SiteConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteConfigServiceImpl implements SiteConfigService {

    private final SiteConfigRepository siteConfigRepository;

    @org.springframework.beans.factory.annotation.Value("${turnstile.site-key:}")
    private String turnstileSiteKey;

    private static final Set<String> NUMERIC_KEYS = Set.of();
    private static final Set<String> THEME_COLORS = Set.of("blue", "coral", "orange", "emerald", "pink", "purple");
    private static final Set<String> APPEARANCE_URL_KEYS = Set.of("hero_image_url", "hero_mobile_image_url", "hero_link_url");

    private static final List<String> PUBLIC_KEYS = List.of(
            "site_name", "site_slogan", "site_description", "logo_url", "favicon_url",
            "announcement_enabled", "announcement", "popup_enabled", "popup_content",
            "contact_email", "contact_telegram", "contact_telegram_group",
            "maintenance_enabled", "maintenance_message", "footer_text", "github_url", "custom_css",
            "store_theme_color", "hero_enabled", "hero_image_url", "hero_mobile_image_url", "hero_link_url"
    );

    /** F16: 管理员允许编辑的配置键白名单 — 防止写入系统内部键或注入任意配置 */
    private static final Set<String> EDITABLE_KEYS = Set.of(
            // 站点基础
            "site_name", "site_slogan", "site_description", "logo_url", "favicon_url",
            "store_theme_color", "hero_enabled", "hero_image_url", "hero_mobile_image_url", "hero_link_url",
            // 公告 / 弹窗
            "announcement_enabled", "announcement", "popup_enabled", "popup_content",
            // 联系方式
            "contact_email", "contact_telegram", "contact_telegram_group",
            // 维护模式
            "maintenance_enabled", "maintenance_message",
            // 页脚 / 外链
            "footer_text", "github_url",
            // 自定义样式
            "custom_css",
            // 系统参数
            "order_expire_minutes", "max_pending_orders_per_user", "max_pending_orders_per_ip",
            "rate_limit_per_second"
    );

    /** F15: CSS 危险模式 — 用于过滤 custom_css 中的 XSS 向量 */
    private static final Pattern CSS_DANGEROUS_PATTERNS = Pattern.compile(
            "(?i)(expression\\s*\\(|javascript\\s*:|@import\\s|\\\\00|behavior\\s*:|" +
            "-moz-binding\\s*:|url\\s*\\(\\s*[\"']?\\s*javascript)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public Map<String, Object> getPublicConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : PUBLIC_KEYS) {
            siteConfigRepository.findByConfigKey(key).ifPresent(c -> {
                String val = c.getConfigValue();
                if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
                    result.put(key, Boolean.parseBoolean(val));
                } else if (NUMERIC_KEYS.contains(key)) {
                    try {
                        result.put(key, Integer.parseInt(val));
                    } catch (NumberFormatException e) {
                        result.put(key, val);
                    }
                } else {
                    result.put(key, val);
                }
            });
        }
        result.putIfAbsent("store_theme_color", "blue");
        result.putIfAbsent("hero_enabled", true);
        result.putIfAbsent("hero_image_url", "/images/store/hero-key.webp");
        // F15: 对 custom_css 进行安全过滤，防止存储型 XSS
        if (result.containsKey("custom_css") && result.get("custom_css") instanceof String css) {
            result.put("custom_css", sanitizeCss(css));
        }
        // Turnstile Site Key：仅在后台开关启用时才返回给前端，确保前后端状态一致
        boolean turnstileEnabled = siteConfigRepository.findByConfigKey("turnstile_enabled")
                .map(c -> "true".equalsIgnoreCase(c.getConfigValue()))
                .orElse(false);
        if (turnstileEnabled && turnstileSiteKey != null && !turnstileSiteKey.isBlank()) {
            result.put("turnstile_site_key", turnstileSiteKey);
        }
        return result;
    }

    @Override
    public List<?> getAllConfigs() {
        return siteConfigRepository.findAll().stream()
                .filter(c -> !c.getConfigKey().startsWith("points_") && !c.getConfigKey().contains("txid"))
                .map(c -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("config_key", c.getConfigKey());
                    map.put("config_value", c.getConfigValue());
                    map.put("config_group", c.getConfigGroup());
                    return map;
                }).toList();
    }

    @Override
    @Transactional
    public void updateConfigs(List<Map<String, String>> configs) {
        if (configs == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少配置内容");
        }
        // Validate the complete request before any write, including in non-proxied callers.
        for (Map<String, String> item : configs) {
            if (item == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的配置项");
            String key = item.get("config_key");
            String value = item.get("config_value");
            if ("store_theme_color".equals(key) && !THEME_COLORS.contains(value == null ? "" : value)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择有效的主题配色");
            }
            if ("hero_enabled".equals(key) && !"true".equals(value) && !"false".equals(value)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "横幅开关必须为 true 或 false");
            }
            if (APPEARANCE_URL_KEYS.contains(key == null ? "" : key) && !isSafeAppearanceUrl(value)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "横幅图片和链接仅支持站内路径或 HTTP/HTTPS 地址");
            }
        }
        for (Map<String, String> item : configs) {
            String key = item.get("config_key");
            String value = item.get("config_value");
            // F16: 只允许白名单内的 key 被修改，防止注入系统内部配置
            if (key == null || !EDITABLE_KEYS.contains(key)) {
                log.warn("Rejected config update for non-editable key: {}", key);
                continue;
            }
            // F15: custom_css 写入时也做安全过滤
            if ("custom_css".equals(key) && value != null) {
                value = sanitizeCss(value);
            }
            SiteConfig config = siteConfigRepository.findByConfigKey(key)
                    .orElseGet(() -> {
                        SiteConfig c = new SiteConfig();
                        c.setConfigKey(key);
                        c.setConfigGroup("site");
                        return c;
                    });
            config.setConfigValue(value);
            siteConfigRepository.save(config);
        }
    }

    private boolean isSafeAppearanceUrl(String value) {
        if (value == null) return false;
        if (value.isEmpty()) return true;
        if (!value.equals(value.trim()) || value.indexOf('\\') >= 0 || value.chars().anyMatch(c -> c <= 32 || c == 127)) return false;
        try {
            URI uri = URI.create(value);
            if (value.startsWith("/") && !value.startsWith("//")) return !uri.isAbsolute() && uri.getRawAuthority() == null;
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    @Transactional
    public void toggleMaintenance(boolean enabled) {
        SiteConfig config = siteConfigRepository.findByConfigKey("maintenance_enabled")
                .orElseGet(() -> {
                    SiteConfig c = new SiteConfig();
                    c.setConfigKey("maintenance_enabled");
                    c.setConfigGroup("site");
                    return c;
                });
        config.setConfigValue(String.valueOf(enabled));
        siteConfigRepository.save(config);
    }

    /**
     * 过滤 CSS 中的危险内容，防止存储型 XSS。
     * 移除 HTML 标签和已知 CSS XSS 向量（expression/javascript:/behavior 等）。
     */
    private String sanitizeCss(String css) {
        if (css == null) return null;
        // 移除所有 HTML 标签（防止 </style><script>... 注入）
        css = css.replaceAll("<[^>]*>", "");
        // 移除危险 CSS 模式
        css = CSS_DANGEROUS_PATTERNS.matcher(css).replaceAll("/* blocked */");
        return css;
    }
}
