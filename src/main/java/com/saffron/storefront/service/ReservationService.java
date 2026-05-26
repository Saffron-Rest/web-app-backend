package com.saffron.storefront.service;

import com.saffron.storefront.domain.Reservation;
import com.saffron.storefront.repository.ReservationRepository;
import com.saffron.storefront.web.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists and validates table-reservation requests submitted from the
 * storefront.
 *
 * Validation is intentionally strict on shape but permissive on semantics
 * — opening hours are *not* enforced here because the restaurant might
 * occasionally accept off-hours private bookings. Anything obviously
 * malformed is rejected with {@link BadRequestException} so the FE can
 * surface a friendly error.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    /** Hard cap so a single request can't accidentally reserve the whole house. */
    private static final int MAX_GUESTS = 40;

    private final ReservationRepository repository;

    public ReservationService(ReservationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Map<String, Object> create(CreateReservationRequest req) {
        if (req == null) throw new BadRequestException("Reservation payload missing");

        Reservation r = new Reservation();
        r.setDate(parseDate(req.date));
        r.setTime(parseTime(req.time));
        r.setGuests(validatedGuests(req.guests));
        r.setName(required(req.name, "name", 120));
        r.setPhone(required(req.phone, "phone", 40));
        r.setEmail(trimmed(req.email, 200));
        r.setNotes(trimmed(req.notes, 500));

        // Soft rule: can't reserve in the past. Accepts today regardless of time
        // because the diner may be looking at their phone after the slot opened.
        if (r.getDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Reservation date is in the past");
        }

        Reservation saved = repository.save(r);
        log.info(
                "Reservation accepted id={} date={} time={} guests={} phone={}",
                saved.getId(),
                saved.getDate(),
                saved.getTime(),
                saved.getGuests(),
                saved.getPhone());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("status", saved.getStatus().name());
        response.put("date", saved.getDate().toString());
        response.put("time", saved.getTime().toString());
        response.put("guests", saved.getGuests());
        response.put("name", saved.getName());
        response.put("createdAt", saved.getCreatedAt().toString());
        return response;
    }

    /* -------------------------------------------------------------------- */
    /* Validation helpers                                                   */
    /* -------------------------------------------------------------------- */

    private static String required(String raw, String field, int max) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Field '" + field + "' is required");
        }
        String v = raw.trim();
        if (v.length() > max) v = v.substring(0, max);
        return v;
    }

    private static String trimmed(String raw, int max) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) v = v.substring(0, max);
        return v;
    }

    private static int validatedGuests(Integer n) {
        if (n == null || n < 1) {
            throw new BadRequestException("Guest count must be at least 1");
        }
        if (n > MAX_GUESTS) {
            throw new BadRequestException(
                    "For groups larger than " + MAX_GUESTS + " please call the restaurant directly.");
        }
        return n;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) throw new BadRequestException("Date is required");
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid date format, expected YYYY-MM-DD");
        }
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) throw new BadRequestException("Time is required");
        try {
            String trimmed = s.trim();
            // Accept "HH:mm" and "HH:mm:ss".
            if (trimmed.length() == 5) trimmed = trimmed + ":00";
            return LocalTime.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid time format, expected HH:mm");
        }
    }

    /** Wire-format payload used by {@link com.saffron.storefront.controller.PublicReservationController}. */
    public static class CreateReservationRequest {
        public String date;
        public String time;
        public Integer guests;
        public String name;
        public String phone;
        public String email;
        public String notes;
    }
}
