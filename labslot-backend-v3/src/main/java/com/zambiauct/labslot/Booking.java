package com.zambiauct.labslot;

import java.time.Duration;
import java.time.LocalDateTime;

public class Booking {
    private final String id;
    private final Student student;
    private final Equipment equipment;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    private BookingStatus status;
    private boolean approvedBySupervisor;
    private boolean checkedIn;
    private LocalDateTime checkedInAt;

    public Booking(String id, Student student, Equipment equipment,
                   LocalDateTime startTime, LocalDateTime endTime) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Booking ID is required");
        }
        if (student == null) {
            throw new IllegalArgumentException("Student is required");
        }
        if (equipment == null) {
            throw new IllegalArgumentException("Equipment is required");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Booking times are required");
        }
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Booking start time must be before end time");
        }

        this.id = id;
        this.student = student;
        this.equipment = equipment;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = BookingStatus.PENDING;
        this.approvedBySupervisor = false;
        this.checkedIn = false;
    }

    public String getId() { return id; }
    public Student getStudent() { return student; }
    public Equipment getEquipment() { return equipment; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public BookingStatus getStatus() { return status; }
    public boolean isApprovedBySupervisor() { return approvedBySupervisor; }
    public boolean isCheckedIn() { return checkedIn; }
    public LocalDateTime getCheckedInAt() { return checkedInAt; }

    public void approveBySupervisor() {
        this.approvedBySupervisor = true;
        if (this.status == BookingStatus.PENDING) {
            this.status = BookingStatus.ACTIVE;
        }
    }

    public void checkIn() {
        if (equipment.isRequiresSupervisorApproval() && !approvedBySupervisor) {
            throw new IllegalStateException("This booking needs supervisor approval before check-in.");
        }

        this.checkedIn = true;
        this.checkedInAt = LocalDateTime.now();
        this.status = BookingStatus.ACTIVE;
    }

    public void complete() {
        this.status = BookingStatus.COMPLETED;
        this.checkedIn = false;
    }

    public void expire() {
        this.status = BookingStatus.EXPIRED;
    }

    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return !this.startTime.isAfter(otherEnd) && !otherStart.isAfter(this.endTime);
    }

    // A no-show is a booking nobody checked in for within 15 minutes of its start.
    // This covers approved bookings too (approval makes them ACTIVE).
    public boolean isNoShowExpired(LocalDateTime now) {
        boolean notYetUsed = !this.checkedIn
                && (this.status == BookingStatus.PENDING || this.status == BookingStatus.ACTIVE);
        return notYetUsed && now.isAfter(this.startTime.plusMinutes(15));
    }

    // Overdue = the item was checked out and is still not returned after the booked end time.
    public boolean isOverdue(LocalDateTime now) {
        return this.checkedIn && this.status == BookingStatus.ACTIVE && now.isAfter(this.endTime);
    }

    public long minutesLate(LocalDateTime now) {
        return isOverdue(now) ? Duration.between(this.endTime, now).toMinutes() : 0;
    }

    @Override
    public String toString() {
        return "Booking[" + id + "] " + student.getName() + " -> " + equipment.getName() + " (" + status + ")";
    }
}
