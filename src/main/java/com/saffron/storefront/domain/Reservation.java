package com.saffron.storefront.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A table reservation request submitted from the public storefront.
 *
 * The lifecycle is intentionally simple: every submission lands as
 * {@link Status#PENDING}. The restaurant team confirms / declines manually
 * (out-of-band by phone). Once a proper admin UI is built we'll move
 * confirmation here and persist the resolved status + confirmation note.
 */
@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "ix_reservations_date", columnList = "reservation_date"),
        @Index(name = "ix_reservations_phone", columnList = "phone"),
})
public class Reservation {

    public enum Status {
        PENDING,
        CONFIRMED,
        CANCELLED
    }

    @Id
    @UuidGenerator
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    @Column(name = "reservation_date", nullable = false)
    private LocalDate date;

    @Column(name = "reservation_time", nullable = false)
    private LocalTime time;

    @Column(nullable = false)
    private int guests;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(length = 200)
    private String email;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Reservation() {}

    public String getId() { return id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getTime() { return time; }
    public void setTime(LocalTime time) { this.time = time; }
    public int getGuests() { return guests; }
    public void setGuests(int guests) { this.guests = guests; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
