package io.github.tuck1297.kioskpos.web;

import io.github.tuck1297.kioskpos.api.MenuApi;
import io.github.tuck1297.kioskpos.api.model.MenuCategory;
import io.github.tuck1297.kioskpos.api.model.MenuItemPage;
import io.github.tuck1297.kioskpos.common.ApiMappers;
import io.github.tuck1297.kioskpos.common.Cursor;
import io.github.tuck1297.kioskpos.menu.MenuCatalog;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot.ItemSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Menu endpoints. Not overridden yet (createMenuItem, updateMenuItem) answer 501 from the
 * generated defaults; when they are built they must call {@link MenuCatalog#evict}.
 */
@RestController
public class MenuController implements MenuApi {

    private static final int DEFAULT_LIMIT = 25;

    private final MenuCatalog catalog;

    public MenuController(MenuCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Served from the cached snapshot (a location's menu is tens of items), filtered and
     * paged in memory. Items are ordered by id as text, which matches how the cursor compares.
     */
    @Override
    public ResponseEntity<MenuItemPage> listMenuItems(UUID locationId, Boolean available, MenuCategory category,
                                                      String cursor, Integer limit) {
        MenuSnapshot menu = catalog.cached(locationId);
        String after = cursor == null ? null : Cursor.decode(cursor).toString();
        int pageSize = limit == null ? DEFAULT_LIMIT : limit;

        List<ItemSnapshot> matching = menu.items().stream()
                .filter(i -> available == null || i.available() == available)
                .filter(i -> category == null || i.category().equals(category.getValue()))
                .sorted(Comparator.comparing(i -> i.id().toString()))
                .filter(i -> after == null || i.id().toString().compareTo(after) > 0)
                .toList();

        boolean hasMore = matching.size() > pageSize;
        List<ItemSnapshot> page = hasMore ? matching.subList(0, pageSize) : matching;
        String nextCursor = hasMore ? Cursor.encode(page.getLast().id()) : null;

        return ResponseEntity.ok(new MenuItemPage()
                .items(page.stream().map(i -> ApiMappers.menuItem(i, locationId, menu.currency())).toList())
                .nextCursor(nextCursor));
    }
}
