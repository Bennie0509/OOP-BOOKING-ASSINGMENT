package com.zambiauct.labslot;

public abstract class Equipment {
    private final String equipmentId;
    private String name;
    private boolean restricted;
    private boolean available;
    private String location;

    protected Equipment(String equipmentId, String name, boolean restricted) {
        this(equipmentId, name, "Main Lab", restricted);
    }

    protected Equipment(String equipmentId, String name, String location, boolean restricted) {
        if (equipmentId == null || equipmentId.isBlank()) {
            throw new IllegalArgumentException("Equipment ID is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Equipment name is required");
        }

        this.equipmentId = equipmentId;
        this.name = name;
        this.location = location;
        this.restricted = restricted;
        this.available = true;
    }

    public String getEquipmentId() { return equipmentId; }
    public String getId() { return equipmentId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public boolean isRestricted() { return restricted; }
    public boolean isAvailable() { return available; }
    public boolean isRequiresSupervisorApproval() { return restricted; }

    public void setName(String name) { this.name = name; }
    public void setLocation(String location) { this.location = location; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setRestricted(boolean restricted) { this.restricted = restricted; }

    public abstract boolean validateBooking(int durationMinutes, boolean isSupervisorApproved);
    public abstract String getType();
    public abstract int getMaxBookingHours();

    @Override
    public String toString() {
        return getType() + " [" + equipmentId + "] " + name + " at " + location;
    }
}
