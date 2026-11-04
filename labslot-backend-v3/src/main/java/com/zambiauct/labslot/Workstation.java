package com.zambiauct.labslot;

public class Workstation extends Equipment {
    private static final int MAX_SESSION_MINUTES = 120;
    private final String workstationNumber;

    public Workstation(String equipmentId, String name) {
        this(equipmentId, name, "Main Lab", "WS-" + equipmentId);
    }

    public Workstation(String equipmentId, String name, String location, String workstationNumber) {
        super(equipmentId, name, location, false);
        this.workstationNumber = workstationNumber;
    }

    public String getWorkstationNumber() { return workstationNumber; }

    @Override
    public boolean validateBooking(int durationMinutes, boolean isSupervisorApproved) {
        if (durationMinutes <= 0) {
            throw new BookingException("Booking duration must be greater than zero minutes.");
        }
        if (durationMinutes > MAX_SESSION_MINUTES) {
            throw new BookingException("Workstation session cannot exceed " + MAX_SESSION_MINUTES + " minutes.");
        }
        return true;
    }

    @Override
    public String getType() { return "WORKSTATION"; }

    @Override
    public int getMaxBookingHours() { return 2; }
}
