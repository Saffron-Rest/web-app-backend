package com.saffron.storefront.service;

import com.saffron.storefront.domain.Reservation;
import com.saffron.storefront.repository.ReservationRepository;
import com.saffron.storefront.web.BadRequestException;
import com.saffron.storefront.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminReservationService {

    private final ReservationRepository repo;
    private final StorefrontAuditService audit;

    public AdminReservationService(ReservationRepository repo, StorefrontAuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String statusStr, String from, String to, String q,
                                    int page, int size) {
        Reservation.Status status = parseStatus(statusStr);
        LocalDate fromD = parseDate(from);
        LocalDate toD = parseDate(to);
        Page<Reservation> p = repo.search(status, fromD, toD,
                (q == null || q.isBlank()) ? "" : q.trim(),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200))));
        List<Map<String, Object>> items = p.getContent().stream().map(AdminReservationService::toMap).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", p.getNumber());
        body.put("size", p.getSize());
        body.put("totalElements", p.getTotalElements());
        body.put("totalPages", p.getTotalPages());
        return body;
    }

    @Transactional
    public Map<String, Object> updateStatus(String id, Reservation.Status status, HttpServletRequest http) {
        Reservation r = repo.findById(id).orElseThrow(() -> new NotFoundException("Reservation not found"));
        r.setStatus(status);
        repo.save(r);
        audit.record("RESERVATION_STATUS", "Reservation", r.getId(), "status=" + status.name(), http);
        return toMap(r);
    }

    @Transactional
    public void delete(String id, HttpServletRequest http) {
        Reservation r = repo.findById(id).orElseThrow(() -> new NotFoundException("Reservation not found"));
        repo.delete(r);
        audit.record("RESERVATION_DELETE", "Reservation", r.getId(), null, http);
    }

    private static Reservation.Status parseStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Reservation.Status.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + s);
        }
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); }
        catch (DateTimeParseException e) { throw new BadRequestException("Invalid date: " + s); }
    }

    public static Map<String, Object> toMap(Reservation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("date", r.getDate().toString());
        m.put("time", r.getTime().toString());
        m.put("guests", r.getGuests());
        m.put("name", r.getName());
        m.put("phone", r.getPhone());
        m.put("email", r.getEmail());
        m.put("notes", r.getNotes());
        m.put("status", r.getStatus().name());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }
}
