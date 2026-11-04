package com.zambiauct.labslot;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BookingManager {
    private final List<Booking> bookings = new ArrayList<>();
    private int nextNumber = 1;

    // What happened when a supervisor confirmed a return.
    public record ReturnResult(int lateMinutes, boolean penaltyAdded, boolean suspended) { }

    public synchronized Booking createBooking(Student student, Equipment equipment,
                                              LocalDateTime startTime, LocalDateTime endTime)
            throws BookingException {
        return createBooking(student, equipment, startTime, endTime, LocalDateTime.now());
    }

    public synchronized Booking createBooking(Student student, Equipment equipment,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              LocalDateTime now)
            throws BookingException {
        if (student == null) {
            throw new BookingException("Student is required");
        }
        if (equipment == null) {
            throw new BookingException("Equipment is required");
        }
        if (startTime == null || endTime == null) {
            throw new BookingException("Start and end times are required");
        }
        if (student.isSuspended(now)) {
            throw new BookingException("Booking is suspended for " + student.getName() + " until "
                    + student.getSuspendedUntil().toLocalDate() + " because of repeated late returns.");
        }
        if (!equipment.isAvailable()) {
            throw new BookingException(equipment.getName() + " is not available.");
        }

        // Polymorphism: each kind of equipment applies its own rules to the duration.
        // Restricted equipment is booked first and approved later, so approval is
        // checked again at check-in (see checkIn below).
        int minutes = (int) Duration.between(startTime, endTime).toMinutes();
        equipment.validateBooking(minutes, true);

        // Polymorphism: each kind of user has its own limit on active bookings.
        int active = 0;
        for (Booking existing : bookings) {
            if (existing.getStudent().getId().equals(student.getId())
                    && (existing.getStatus() == BookingStatus.PENDING
                    || existing.getStatus() == BookingStatus.ACTIVE)) {
                active++;
            }
        }
        if (active >= student.getMaxActiveBookings()) {
            throw new BookingException(student.getName() + " already has the maximum of "
                    + student.getMaxActiveBookings() + " active bookings.");
        }

        // Double bookings are blocked for everyone, not just the same student.
        for (Booking existing : bookings) {
            if (existing.getEquipment().getId().equals(equipment.getId())
                    && existing.getStatus() != BookingStatus.COMPLETED
                    && existing.getStatus() != BookingStatus.EXPIRED
                    && existing.overlaps(startTime, endTime)) {
                throw new BookingException(equipment.getName() + " is already booked at that time.");
            }
        }

        Booking booking = new Booking(
                "BKG-" + nextNumber++,
                student,
                equipment,
                startTime,
                endTime
        );

        bookings.add(booking);
        return booking;
    }

    public synchronized List<Booking> getAllBookings() {
        return new ArrayList<>(bookings);
    }

    public synchronized List<Booking> getBookingsForStudent(String studentId) {
        List<Booking> result = new ArrayList<>();
        for (Booking booking : bookings) {
            if (booking.getStudent().getId().equals(studentId)) {
                result.add(booking);
            }
        }
        return result;
    }

    public synchronized Booking getBooking(String bookingId) throws BookingException {
        return findBookingById(bookingId);
    }

    // The booking that currently has this item in someone's hands, or null if it is on the shelf.
    public synchronized Booking findCheckedOut(String equipmentId) {
        for (Booking booking : bookings) {
            if (booking.getEquipment().getId().equals(equipmentId)
                    && booking.isCheckedIn()
                    && booking.getStatus() == BookingStatus.ACTIVE) {
                return booking;
            }
        }
        return null;
    }

    public synchronized void approveBooking(String bookingId) throws BookingException {
        Booking booking = findBookingById(bookingId);
        booking.approveBySupervisor();
    }

    // Only a user whose canApprove() is true (a LabSupervisor) may approve.
    public synchronized void approveBooking(String bookingId, User approver) throws BookingException {
        if (approver == null || !approver.canApprove()) {
            throw new BookingException("Only a lab supervisor can approve bookings.");
        }
        Booking booking = findBookingById(bookingId);
        requireOpen(booking);
        if (!booking.getEquipment().isRequiresSupervisorApproval()) {
            throw new BookingException(booking.getEquipment().getName() + " does not need approval.");
        }
        booking.approveBySupervisor();
    }

    public synchronized void checkIn(String bookingId) throws BookingException {
        Booking booking = findBookingById(bookingId);
        requireOpen(booking);

        Booking holder = findCheckedOut(booking.getEquipment().getId());
        if (holder != null && !holder.getId().equals(booking.getId())) {
            throw new BookingException(booking.getEquipment().getName()
                    + " has not been returned yet. A supervisor must confirm its return first.");
        }

        int minutes = (int) Duration.between(booking.getStartTime(), booking.getEndTime()).toMinutes();
        booking.getEquipment().validateBooking(minutes, booking.isApprovedBySupervisor());
        booking.checkIn();
    }

    public synchronized void completeBooking(String bookingId) throws BookingException {
        Booking booking = findBookingById(bookingId);
        requireOpen(booking);
        if (!booking.isCheckedIn()) {
            throw new BookingException("The student must check in before the booking can be completed.");
        }
        booking.complete();
    }

    // The item comes back and a supervisor confirms it, so the lab always knows.
    // If it is later than the grace period, the student earns a penalty point.
    public synchronized ReturnResult confirmReturn(String bookingId, User supervisor, LocalDateTime now)
            throws BookingException {
        if (supervisor == null || !supervisor.canApprove()) {
            throw new BookingException("Only a lab supervisor can confirm a return.");
        }
        Booking booking = findBookingById(bookingId);
        requireOpen(booking);
        if (!booking.isCheckedIn()) {
            throw new BookingException("This item has not been checked out yet.");
        }

        int lateMinutes = (int) booking.minutesLate(now);
        booking.complete();

        Student student = booking.getStudent();
        boolean penaltyAdded = student.recordLateReturn(lateMinutes, now);
        return new ReturnResult(lateMinutes, penaltyAdded, student.isSuspended(now));
    }

    // A booking can only be cancelled while the item is still in the lab.
    public synchronized void cancelBooking(String bookingId) throws BookingException {
        Booking booking = findBookingById(bookingId);
        requireOpen(booking);
        if (booking.isCheckedIn()) {
            throw new BookingException("This item is checked out. It must be returned and confirmed by a supervisor first.");
        }
        bookings.remove(booking);
    }

    // Used when loading saved bookings from the file at startup.
    public synchronized void restoreBooking(Booking booking) {
        bookings.add(booking);
        String id = booking.getId();
        if (id.startsWith("BKG-")) {
            try {
                nextNumber = Math.max(nextNumber, Integer.parseInt(id.substring(4)) + 1);
            } catch (NumberFormatException ignored) {
                // not one of our generated ids; keep the counter as it is
            }
        }
    }

    // Returns how many bookings were expired, so callers know whether to save.
    public synchronized int expireNoShows(LocalDateTime now) {
        int expired = 0;
        for (Booking booking : bookings) {
            if (booking.isNoShowExpired(now)) {
                booking.expire();
                expired++;
            }
        }
        return expired;
    }

    private void requireOpen(Booking booking) throws BookingException {
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BookingException("Booking " + booking.getId() + " is already " + booking.getStatus() + ".");
        }
    }

    private Booking findBookingById(String bookingId) throws BookingException {
        for (Booking booking : bookings) {
            if (booking.getId().equals(bookingId)) {
                return booking;
            }
        }
        throw new BookingException("Booking not found: " + bookingId);
    }
}
