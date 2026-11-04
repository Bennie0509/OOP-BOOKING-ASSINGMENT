package com.zambiauct.labslot;

import java.time.LocalDateTime;

public class Student extends User {
    private final String studentNumber;
    private String program;

    // Late-return rules. A return later than the grace period earns a penalty point.
    // Reaching the limit suspends booking for a while and the points start again.
    public static final int GRACE_MINUTES = 5;
    public static final int POINTS_BEFORE_SUSPENSION = 3;
    public static final int SUSPENSION_DAYS = 7;

    private int penaltyPoints;
    private LocalDateTime suspendedUntil;

    public Student(String id, String name, String email,
                   String studentNumber, String program) {
        super(id, name, email);
        this.studentNumber = studentNumber;
        this.program = program;
    }

    public String getStudentNumber() { return studentNumber; }
    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }

    public int getPenaltyPoints() { return penaltyPoints; }
    public LocalDateTime getSuspendedUntil() { return suspendedUntil; }

    public boolean isSuspended(LocalDateTime now) {
        return suspendedUntil != null && now.isBefore(suspendedUntil);
    }

    // Returns true if a penalty point was added.
    public boolean recordLateReturn(int lateMinutes, LocalDateTime now) {
        if (lateMinutes <= GRACE_MINUTES) {
            return false;
        }
        penaltyPoints++;
        if (penaltyPoints >= POINTS_BEFORE_SUSPENSION) {
            suspendedUntil = now.plusDays(SUSPENSION_DAYS);
            penaltyPoints = 0;
        }
        return true;
    }

    // Used when loading saved penalties from the file at startup.
    public void restorePenalties(int points, LocalDateTime until) {
        this.penaltyPoints = Math.max(0, points);
        this.suspendedUntil = until;
    }

    @Override
    public String getRole() { return "STUDENT"; }

    @Override
    public int getMaxActiveBookings() { return 2; }

    @Override
    public boolean canApprove() { return false; }

    public static void main(String[] args) {
        Student student = new Student(
                "S-100",
                "Alice",
                "alice@example.com",
                "2024001",
                "Computer Science"
        );

        LabSupervisor supervisor = new LabSupervisor(
                "L-200",
                "Mr. Smith",
                "smith@example.com",
                "EMP-77",
                "Main Lab"
        );

        System.out.println(student.getRole() + " -> " + student.getMaxActiveBookings());
        System.out.println(supervisor.getRole() + " -> " + supervisor.getLabName());
    }
}