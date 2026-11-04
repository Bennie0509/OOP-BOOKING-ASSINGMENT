import java.time.LocalDateTime;

public class Booking {
    private static int nextId = 1;

    private final int bookingId;
    private final String userName;
    private final String equipmentName;   // e.g. "Dell Workstation" or "Prusa MK4"
    private final LocalDateTime start;
    private final LocalDateTime end;

    public Booking(String userName, String equipmentName, LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }
        this.bookingId = nextId++;
        this.userName = userName;
        this.equipmentName = equipmentName;
        this.start = start;
        this.end = end;
    }

    public int getBookingId()          { return bookingId; }
    public String getUserName()        { return userName; }
    public String getEquipmentName()   { return equipmentName; }
    public LocalDateTime getStart()    { return start; }
    public LocalDateTime getEnd()      { return end; }

    /** True if this booking overlaps the given time range. */
    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    @Override
    public String toString() {
        return "Booking #" + bookingId + ": " + userName + " booked " + equipmentName
                + " from " + start + " to " + end;
    }
}