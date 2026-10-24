package io.github.tuck1297.kioskpos.web;

import io.github.tuck1297.kioskpos.api.LocationsApi;
import io.github.tuck1297.kioskpos.api.model.Location;
import io.github.tuck1297.kioskpos.api.model.LocationPage;
import io.github.tuck1297.kioskpos.common.ApiMappers;
import io.github.tuck1297.kioskpos.common.Cursor;
import io.github.tuck1297.kioskpos.location.LocationEntity;
import io.github.tuck1297.kioskpos.location.LocationRepository;
import io.github.tuck1297.kioskpos.menu.MenuCatalog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocationsController implements LocationsApi {

    private static final int DEFAULT_LIMIT = 25;

    private final LocationRepository locations;
    private final MenuCatalog catalog;

    public LocationsController(LocationRepository locations, MenuCatalog catalog) {
        this.locations = locations;
        this.catalog = catalog;
    }

    @Override
    public ResponseEntity<LocationPage> listLocations(String cursor, Integer limit) {
        int pageSize = limit == null ? DEFAULT_LIMIT : limit;
        // Fetch one extra row to know whether another page exists.
        Limit fetch = Limit.of(pageSize + 1);
        List<LocationEntity> rows = cursor == null
                ? locations.findAllByOrderByIdAsc(fetch)
                : locations.findByIdGreaterThanOrderByIdAsc(Cursor.decode(cursor), fetch);

        boolean hasMore = rows.size() > pageSize;
        List<LocationEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore ? Cursor.encode(page.getLast().getId()) : null;

        return ResponseEntity.ok(new LocationPage()
                .items(page.stream().map(LocationsController::toApi).toList())
                .nextCursor(nextCursor));
    }

    /** Every register loads its location on start-up, so this is served from the cache. */
    @Override
    public ResponseEntity<Location> getLocation(UUID locationId) {
        return ResponseEntity.ok(ApiMappers.location(catalog.cached(locationId)));
    }

    static Location toApi(LocationEntity e) {
        return ApiMappers.location(e);
    }
}
