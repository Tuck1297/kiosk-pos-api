package io.github.tuck1297.kioskpos.guard;

import io.github.tuck1297.kioskpos.api.model.OrderLineRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.validation.Errors;

/** Basket rules shared by the quote and create-order guards, so the two can't drift apart. */
final class OrderLineRules {

    /** Per order, across all lines (each line is already capped at 20 by the schema). */
    static final int MAX_ITEMS_PER_ORDER = 60;

    private OrderLineRules() {
    }

    static void check(List<OrderLineRequest> lines, Errors errors) {
        if (lines == null) {
            return; // the schema's required check reports this
        }
        int totalQuantity = 0;
        Set<UUID> plainItems = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            OrderLineRequest line = lines.get(i);
            if (line == null || line.getMenuItemId() == null || line.getQuantity() == null) {
                continue; // schema reports missing fields
            }
            totalQuantity += line.getQuantity();
            boolean hasNote = line.getNote() != null && !line.getNote().isBlank();
            // The same item twice without notes is a register bug (it should bump quantity).
            if (!hasNote && !plainItems.add(line.getMenuItemId())) {
                errors.rejectValue("lines[" + i + "].menuItemId", "duplicate",
                        "appears on more than one line; increase the quantity instead");
            }
        }
        if (totalQuantity > MAX_ITEMS_PER_ORDER) {
            errors.rejectValue("lines", "too_many", "must total at most " + MAX_ITEMS_PER_ORDER + " items");
        }
    }
}
