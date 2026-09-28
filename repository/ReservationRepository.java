package com.medifind.repository;

import com.medifind.entity.Reservation;
import com.medifind.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SLP: Core Platform & Shared Engine → "Implement Reservation Ledger core
 * service"
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByConfirmationCode(String confirmationCode);

    /**
     * SLP: Patient Reservation Management → "Reservation history with
     * date/status filters"
     * Both filters are optional, same "(:param IS NULL OR ...)" pattern
     * used elsewhere in this project. {@code JOIN FETCH} on pharmacy and
     * medicine loads them alongside each row instead of leaving lazy
     * proxies behind — both the patient dashboard and this history page
     * print {@code r.pharmacy.pharmacyName} / {@code r.medicine.name} in
     * Thymeleaf, which runs after the Hibernate session used here has
     * already closed (open-in-view is off). An explicit countQuery
     * (without the fetch joins) keeps pagination's row count correct.
     */
    @Query(value = "SELECT r FROM Reservation r JOIN FETCH r.pharmacy JOIN FETCH r.medicine " +
            "WHERE r.patient.id = :patientId " +
            "AND (:status IS NULL OR r.status = :status) " +
            "AND (:from IS NULL OR r.reservedAt >= :from) " +
            "AND (:to IS NULL OR r.reservedAt <= :to) " +
            "ORDER BY r.reservedAt DESC",
            countQuery = "SELECT COUNT(r) FROM Reservation r WHERE r.patient.id = :patientId " +
            "AND (:status IS NULL OR r.status = :status) " +
            "AND (:from IS NULL OR r.reservedAt >= :from) " +
            "AND (:to IS NULL OR r.reservedAt <= :to)")
    Page<Reservation> findHistoryForPatient(@Param("patientId") Long patientId,
                                             @Param("status") ReservationStatus status,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to,
                                             Pageable pageable);

    /**
     * SLP: Pharmacy Reservation Fulfilment → "View incoming reservations
     * in real time", "Reservation queue in chronological order"
     */
    List<Reservation> findByPharmacyIdAndStatusOrderByReservedAtAsc(Long pharmacyId, ReservationStatus status);

    /**
     * SLP: Admin Oversight & Complaint Handling → "View and cancel a disputed reservation"
     *
     * JOIN FETCH on patient, pharmacy and medicine for the same reason as
     * {@link #findHistoryForPatient} above — admin/reservations.html
     * prints all three of {@code r.patient.fullName}, {@code
     * r.pharmacy.pharmacyName} and {@code r.medicine.name}.
     */
    @Query(value = "SELECT r FROM Reservation r JOIN FETCH r.patient JOIN FETCH r.pharmacy JOIN FETCH r.medicine " +
            "WHERE (:status IS NULL OR r.status = :status) " +
            "ORDER BY r.reservedAt DESC",
            countQuery = "SELECT COUNT(r) FROM Reservation r WHERE (:status IS NULL OR r.status = :status)")
    Page<Reservation> searchForAdmin(@Param("status") ReservationStatus status, Pageable pageable);

    /** SLP: Admin Oversight & Complaint Handling → "full CRUD" — guards pharmacy deletion so reservation history is never silently destroyed. */
    boolean existsByPharmacyId(Long pharmacyId);

    /**
     * SLP: Patient Reservation Management → "Auto-expire reservations
     * past the pickup window"
     * Used by the scheduled job in ReservationService.
     */
    List<Reservation> findByStatusAndPickupDeadlineBefore(ReservationStatus status, LocalDateTime cutoff);

    long countByStatus(ReservationStatus status);

    /**
     * SLP: Admin Reporting & Configuration → "Generate platform-wide
     * reports" ("Daily reservations")
     *
     * A native query (real SQL, not JPQL) because DATE(...) grouping is a
     * database function rather than something the JPA entity model
     * understands — acceptable here since the project intentionally
     * targets MySQL only (see the pom.xml/application.properties).
     * Returns rows of [reservation_date, count] for the last N days.
     */
    @Query(value = "SELECT DATE(reserved_at) AS reservation_date, COUNT(*) AS total " +
            "FROM reservations " +
            "WHERE reserved_at >= :since " +
            "GROUP BY DATE(reserved_at) " +
            "ORDER BY reservation_date ASC", nativeQuery = true)
    List<Object[]> countDailyReservationsSince(@Param("since") LocalDateTime since);

    long countByPharmacyIdAndStatus(Long pharmacyId, ReservationStatus status);
}
