package io.github.tuck1297.kioskpos.web;

import io.github.tuck1297.kioskpos.api.MenuApi;
import io.github.tuck1297.kioskpos.api.model.MenuCategory;
import io.github.tuck1297.kioskpos.api.model.MenuItemPage;
import io.github.tuck1297.kioskpos.common.ApiMappers;
import io.github.tuck1297.kioskpos.common.Cursor;
import io.github.tuck1297.kioskpos.common.NotFoundException;
import io.github.tuck1297.kioskpos.location.LocationEntity;
import io.github.tuck1297.kioskpos.location.LocationRepository;
import io.github.tuck1297.kioskpos.menu.MenuItemEntity;
import io.github.tuck1297.kioskpos.menu.MenuItemRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Menu endpoints. Not overridden yet (createMenuItem, updateMenuItem) answer 501 from the
 * generated defaults.
 */
@RestController
public class MenuController implements MenuApi {

    private static final int DEFAULT_LIMIT = 25;

    private final LocationRepository locations;
    private final MenuItemRepository menuItems;

    public MenuController(LocationRepository locations, MenuItemRepository menuItems) {
        this.locations = locations;
        this.menuItems = menuItems;
    }

    /**
     * A location's menu is small (tens of items), so filter and page in memory. Items are
     * ordered by id as text, which matches how the cursor compares them.
     */
    @Override
    public ResponseEntity<MenuItemPage> listMenuItems(UUID locationId, Boolean available, MenuCategory category,
                                                      String cursor, Integer limit) {
        LocationEntity location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location", locationId));
        String after = cursor == null ? null : Cursor.decode(cursor).toString();
        int pageSize = limit == null ? DEFAULT_LIMIT : limit;

        List<MenuItemEntity> matching = menuItems.findByLocationId(locationId).stream()
                .filter(i -> available == null || i.isAvailable() == available)
                .filter(i -> category == null || i.getCategory().equals(category.getValue()))
                .sorted(Comparator.comparing(i -> i.getId().toString()))
                .filter(i -> after == null || i.getId().toString().compareTo(after) > 0)
                .toList();

        boolean hasMore = matching.size() > pageSize;
        List<MenuItemEntity> page = hasMore ? matching.subList(0, pageSize) : matching;
        String nextCursor = hasMore ? Cursor.encode(page.getLast().getId()) : null;

        return ResponseEntity.ok(new MenuItemPage()
                .items(page.stream().map(i -> ApiMappers.menuItem(i, location.getCurrency())).toList())
                .nextCursor(nextCursor));
    }
}
