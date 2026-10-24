package io.github.tuck1297.kioskpos.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One payment attempt. Declines are kept too, so staff can see what happened. */
@Entity
@Table(name = "payment")
public class PaymentEntity {

    public static final String CAPTURED = "CAPTURED";
    public static final String DECLINED = "DECLINED";

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "tender_type", nullable = false, updatable = false)
    private String tenderType;

    @Column(nullable = false)
    private String status;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "cash_tendered_cents", updatable = false)
    private Long cashTenderedCents;

    @Column(name = "change_due_cents", updatable = false)
    private Long changeDueCents;

    @Column(name = "card_last4", length = 4, updatable = false)
    private String cardLast4;

    @Column(name = "card_brand", updatable = false)
    private String cardBrand;

    @Column(name = "processor_reference", updatable = false)
    private String processorReference;

    @Column(name = "decline_code", updatable = false)
    private String declineCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PaymentEntity() {
        // for JPA
    }

    private PaymentEntity(UUID orderId, String tenderType, String status, long amountCents, String currency,
                          OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.tenderType = tenderType;
        this.status = status;
        this.amountCents = amountCents;
        this.currency = currency;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PaymentEntity cash(UUID orderId, long amountCents, String currency, long tenderedCents,
                                     OffsetDateTime now) {
        PaymentEntity p = new PaymentEntity(orderId, "CASH", CAPTURED, amountCents, currency, now);
        p.cashTenderedCents = tenderedCents;
        p.changeDueCents = tenderedCents - amountCents;
        return p;
    }

    public static PaymentEntity cardCaptured(UUID orderId, long amountCents, String currency, String brand,
                                             String last4, String reference, OffsetDateTime now) {
        PaymentEntity p = new PaymentEntity(orderId, "CARD", CAPTURED, amountCents, currency, now);
        p.cardBrand = brand;
        p.cardLast4 = last4;
        p.processorReference = reference;
        return p;
    }

    public static PaymentEntity cardDeclined(UUID orderId, long amountCents, String currency, String brand,
                                             String last4, String declineCode, OffsetDateTime now) {
        PaymentEntity p = new PaymentEntity(orderId, "CARD", DECLINED, amountCents, currency, now);
        p.cardBrand = brand;
        p.cardLast4 = last4;
        p.declineCode = declineCode;
        return p;
    }

    public boolean isCaptured() {
        return CAPTURED.equals(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getTenderType() {
        return tenderType;
    }

    public String getStatus() {
        return status;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getCashTenderedCents() {
        return cashTenderedCents;
    }

    public Long getChangeDueCents() {
        return changeDueCents;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public String getProcessorReference() {
        return processorReference;
    }

    public String getDeclineCode() {
        return declineCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
