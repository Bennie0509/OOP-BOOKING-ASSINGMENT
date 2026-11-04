import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BookingManager {
    private final List<Booking> bookings = new ArrayList<>();

    /** Checks whether the equipment is free in the given time range. */
    public boolean isAvailable(String equipmentName, LocalDateTime start, LocalDateTime end) {
        for (Booking b : bookings) {
            if (b.getEquipmentName().equalsIgnoreCase(equipmentName) && b.overlaps(start, end)) {
                return false;
            }
        }
        return true;
    }

    /** Creates a booking if the equipment is free; returns null otherwise. */
    public Booking createBooking(String userName, String equipmentName,
                                 LocalDateTime start, LocalDateTime end) {
        if (!isAvailable(equipmentName, start, end)) {
            return null;
        }
        Booking booking = new Booking(userName, equipmentName, start, end);
        bookings.add(booking);
        return booking;
    }

    public boolean cancelBooking(int bookingId) {
        return bookings.removeIf(b -> b.getBookingId() == bookingId);
    }

    public List<Booking> getBookingsForUser(String userName) {
        List<Booking> result = new ArrayList<>();
        for (Booking b : bookings) {
            if (b.getUserName().equalsIgnoreCase(userName)) {
                result.add(b);
            }
        }
        return result;
    }

    public List<Booking> getAllBookings() {
        return new ArrayList<>(bookings);
    }
}