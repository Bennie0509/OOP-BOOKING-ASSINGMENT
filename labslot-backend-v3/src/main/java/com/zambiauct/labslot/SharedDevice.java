package com.zambiauct.labslot;

public class SharedDevice extends Equipment {
    private final String deviceType;

    public SharedDevice(String equipmentId, String name) {
        this(equipmentId, name, "Main Lab", "Shared");
    }

    public SharedDevice(String equipmentId, String name, String location, String deviceType) {
        super(equipmentId, name, location, true);
        this.deviceType = deviceType;
    }

    public String getDeviceType() { return deviceType; }

    @Override
    public boolean validateBooking(int durationMinutes, boolean isSupervisorApproved) {
        if (durationMinutes <= 0) {
            throw new BookingException("Booking duration must be greater than zero minutes.");
        }
        if (isRestricted() && !isSupervisorApproved) {
            throw new BookingException("Restricted device '" + getName() + "' requires supervisor approval.");
        }
        return true;
    }

    @Override
    public String getType() { return "SHARED_DEVICE"; }

    @Override
    public int getMaxBookingHours() { return 2; }
}
