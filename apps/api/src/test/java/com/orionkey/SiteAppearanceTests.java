package com.orionkey;

import com.orionkey.entity.SiteConfig;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.SiteConfigRepository;
import com.orionkey.service.impl.SiteConfigServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteAppearanceTests {
    @Test
    void appearanceIsPersistedAndPubliclyReadable() {
        var repository = mock(SiteConfigRepository.class);
        Map<String, SiteConfig> rows = new HashMap<>();
        when(repository.findByConfigKey(anyString())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        when(repository.save(any())).thenAnswer(call -> { SiteConfig row = call.getArgument(0); rows.put(row.getConfigKey(), row); return row; });
        var service = new SiteConfigServiceImpl(repository);
        service.updateConfigs(List.of(kv("store_theme_color", "emerald"), kv("hero_enabled", "false"),
                kv("hero_image_url", "/api/uploads/banner.webp"), kv("hero_mobile_image_url", "https://example.com/mobile.webp"), kv("hero_link_url", "/product/example")));
        var result = service.getPublicConfig();
        assertEquals("emerald", result.get("store_theme_color"));
        assertEquals(false, result.get("hero_enabled"));
        assertEquals("/api/uploads/banner.webp", result.get("hero_image_url"));
        assertEquals("https://example.com/mobile.webp", result.get("hero_mobile_image_url"));
        assertEquals("/product/example", result.get("hero_link_url"));
    }
    @Test
    void defaultsSupportExistingSitesWithoutDatabaseMigration() {
        var result = new SiteConfigServiceImpl(mock(SiteConfigRepository.class)).getPublicConfig();
        assertEquals("blue", result.get("store_theme_color"));
        assertEquals(true, result.get("hero_enabled"));
        assertEquals("/images/store/hero-key.webp", result.get("hero_image_url"));
    }
    @Test
    void invalidAppearanceValuesAreRejectedBeforeAnyWrite() {
        for (var entry : List.of(kv("store_theme_color", "injected"), kv("hero_enabled", "yes"),
                kv("hero_image_url", "javascript:alert(1)"), kv("hero_mobile_image_url", "data:image/svg+xml,test"),
                kv("hero_link_url", "//evil.example"), kv("hero_link_url", "/\\evil.example"),
                kv("hero_link_url", "https://"), kv("hero_link_url", "https://example.com/\nwrong"))) {
            var repository = mock(SiteConfigRepository.class);
            var service = new SiteConfigServiceImpl(repository);
            assertThrows(BusinessException.class, () -> service.updateConfigs(List.of(kv("site_slogan", "Updated"), entry)), entry.toString());
            verify(repository, never()).save(any());
        }
    }
    private Map<String, String> kv(String key, String value) { return Map.of("config_key", key, "config_value", value); }
}
