package io.github.tuck1297.kioskpos.tax;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A sales tax rate, in basis points (650 = 6.50%), shared by locations in one jurisdiction. */
@Entity
@Table(name = "tax_rate")
public class TaxRateEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "rate_bps", nullable = false)
    private int rateBps;

    protected TaxRateEntity() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getRateBps() {
        return rateBps;
    }
}
