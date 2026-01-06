package com.exe.skillverse_backend.seminar_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seminars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seminar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 5000)
    private String description;

    private String imageUrl;

    @Column(nullable = false)
    private String meetingLink;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private BigDecimal price; // 0 for free

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeminarStatus status;

    @Column(nullable = false)
    private String creatorId;

    /**
     * Maximum number of tickets that can be sold.
     * NULL means unlimited capacity.
     */
    @Column(name = "max_capacity")
    private Integer maxCapacity;

    /**
     * Number of tickets currently sold.
     * Used for atomic capacity checking.
     */
    @Column(name = "tickets_sold", nullable = false)
    @Builder.Default
    private Integer ticketsSold = 0;

    /**
     * Version field for optimistic locking.
     * Prevents race conditions when updating ticketsSold.
     */
    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // === Business Logic Methods ===

    /**
     * Check if this seminar has a capacity limit.
     */
    public boolean hasCapacityLimit() {
        return maxCapacity != null && maxCapacity > 0;
    }

    /**
     * Check if this seminar is sold out.
     */
    public boolean isSoldOut() {
        return hasCapacityLimit() && ticketsSold >= maxCapacity;
    }

    /**
     * Get remaining capacity. Returns null if unlimited.
     */
    public Integer getRemainingCapacity() {
        if (!hasCapacityLimit()) {
            return null; // Unlimited
        }
        return Math.max(0, maxCapacity - ticketsSold);
    }

    /**
     * Check if a ticket can be purchased (capacity check).
     */
    public boolean canPurchaseTicket() {
        return !hasCapacityLimit() || ticketsSold < maxCapacity;
    }

    /**
     * Increment tickets sold. Call this within a transaction.
     * 
     * @throws IllegalStateException if capacity exceeded
     */
    public void incrementTicketsSold() {
        if (hasCapacityLimit() && ticketsSold >= maxCapacity) {
            throw new IllegalStateException("Seminar is sold out. No more tickets available.");
        }
        this.ticketsSold++;
    }

    /**
     * Decrement tickets sold (for refunds).
     */
    public void decrementTicketsSold() {
        if (this.ticketsSold > 0) {
            this.ticketsSold--;
        }
    }
}
