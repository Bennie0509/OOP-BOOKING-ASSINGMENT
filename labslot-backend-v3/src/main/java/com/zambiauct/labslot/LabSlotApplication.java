package com.zambiauct.labslot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LabSlotApplication {
    private static final int PORT = 7070;
    private static final String SESSION_USER = "userId";

    // Everyone who can use the system and everything that can be booked.
    private static final Map<String, User> users = new LinkedHashMap<>();
    private static final Map<String, Student> students = new LinkedHashMap<>();
    private static final Map<String, Equipment> equipment = new LinkedHashMap<>();

    private static final BookingManager manager = new BookingManager();
    private static final LabDataStore userStore = new LabDataStore("data/users.csv");
    private static final LabDataStore bookingStore = new LabDataStore("data/bookings.csv");
    private static final LabDataStore penaltyStore = new LabDataStore("data/penalties.csv");

    // Shapes of the JSON the front end sends us. Note there is no userId in a
    // booking request: the server works out who you are from your login session.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginRequest(String email, String password) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingRequest(String equipmentId, String startTime, int durationMinutes) { }

    // 401 = not logged in, 403 = logged in but not allowed.
    private static class AuthException extends RuntimeException {
        private final int status;

        AuthException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    public static void main(String[] args) {
        seedData();
        loadSavedBookings();

        Javalin app = Javalin.create(config ->
                config.staticFiles.add("/public", Location.CLASSPATH));

        registerAuthCheck(app);
        registerErrorHandlers(app);
        registerRoutes(app);

        app.start(PORT);
        System.out.println("LabSlot is running at http://localhost:" + PORT);
    }

    // ---------------------------------------------------------------
    // Login check: every /api/ call except login and logout needs a session.
    // ---------------------------------------------------------------
    private static void registerAuthCheck(Javalin app) {
        app.before(ctx -> {
            String path = ctx.path();
            boolean isApi = path.startsWith("/api/");
            boolean isOpen = path.equals("/api/login") || path.equals("/api/logout");
            if (isApi && !isOpen && currentUser(ctx) == null) {
                throw new AuthException(401, "Please log in.");
            }
        });
    }

    // ---------------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------------
    private static void registerRoutes(Javalin app) {
        // 1. Log in with email and password
        app.post("/api/login", ctx -> {
            LoginRequest request = ctx.bodyAsClass(LoginRequest.class);
            User user = findByEmail(request.email());
            if (user == null || !user.checkPassword(request.password())) {
                throw new AuthException(401, "Wrong email or password.");
            }
            ctx.req().getSession(true);
            ctx.req().changeSessionId();
            ctx.sessionAttribute(SESSION_USER, user.getId());
            ctx.json(userToMap(user));
        });

        // 2. Log out
        app.post("/api/logout", ctx -> {
            ctx.req().getSession().invalidate();
            ctx.status(204);
        });

        // 3. Who am I? (the page uses this to decide what to show)
        app.get("/api/me", ctx -> ctx.json(userToMap(requireUser(ctx))));

        // 4. What can be booked, and whether it is on the shelf right now
        app.get("/api/equipment", ctx -> {
            User user = requireUser(ctx);
            if (manager.expireNoShows(LocalDateTime.now()) > 0) {
                save();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Equipment item : equipment.values()) {
                result.add(equipmentToMap(item, user.canApprove()));
            }
            ctx.json(result);
        });

        // 5. Bookings: supervisors see all of them, students see only their own
        app.get("/api/bookings", ctx -> {
            User user = requireUser(ctx);
            if (manager.expireNoShows(LocalDateTime.now()) > 0) {
                save();
            }
            List<Booking> visible = user.canApprove()
                    ? manager.getAllBookings()
                    : manager.getBookingsForStudent(user.getId());
            List<Map<String, Object>> result = new ArrayList<>();
            for (Booking booking : visible) {
                result.add(bookingToMap(booking));
            }
            ctx.json(result);
        });

        // 6. Create a booking (students only, always for yourself)
        app.post("/api/bookings", ctx -> {
            User user = requireUser(ctx);
            if (!(user instanceof Student student)) {
                throw new BookingException("Only students can book equipment.");
            }
            BookingRequest request = ctx.bodyAsClass(BookingRequest.class);
            Equipment item = equipment.get(request.equipmentId());
            if (item == null) {
                throw new BookingException("Equipment not found: " + request.equipmentId());
            }
            if (request.startTime() == null || request.startTime().isBlank()) {
                throw new BookingException("Start time is required.");
            }
            LocalDateTime start = LocalDateTime.parse(request.startTime());
            LocalDateTime end = start.plusMinutes(request.durationMinutes());

            Booking booking = manager.createBooking(student, item, start, end);
            save();
            ctx.status(201).json(bookingToMap(booking));
        });

        // 7. Approve a booking for restricted equipment (canApprove() decides who may)
        app.put("/api/bookings/{id}/approve", ctx -> {
            String id = ctx.pathParam("id");
            manager.approveBooking(id, requireUser(ctx));
            save();
            ctx.json(bookingToMap(manager.getBooking(id)));
        });

        // 8. Check in on arrival
        app.put("/api/bookings/{id}/checkin", ctx -> {
            String id = ctx.pathParam("id");
            requireOwnerOrSupervisor(requireUser(ctx), manager.getBooking(id));
            manager.checkIn(id);
            save();
            ctx.json(bookingToMap(manager.getBooking(id)));
        });

        // 9. Supervisor confirms the item was returned (late returns earn a penalty)
        app.put("/api/bookings/{id}/complete", ctx -> {
            String id = ctx.pathParam("id");
            BookingManager.ReturnResult result =
                    manager.confirmReturn(id, requireUser(ctx), LocalDateTime.now());
            save();
            savePenalties();
            Map<String, Object> map = bookingToMap(manager.getBooking(id));
            map.put("returnedLateMinutes", result.lateMinutes());
            map.put("penaltyAdded", result.penaltyAdded());
            map.put("studentSuspended", result.suspended());
            ctx.json(map);
        });

        // 10. Cancel a booking (only while the item is still in the lab)
        app.delete("/api/bookings/{id}", ctx -> {
            String id = ctx.pathParam("id");
            requireOwnerOrSupervisor(requireUser(ctx), manager.getBooking(id));
            manager.cancelBooking(id);
            save();
            ctx.status(204);
        });
    }

    // Turn our own exceptions into clear messages the page can show.
    private static void registerErrorHandlers(Javalin app) {
        app.exception(AuthException.class, (e, ctx) -> sendError(ctx, e.status, e.getMessage()));
        app.exception(BookingException.class, (e, ctx) -> sendError(ctx, 400, e.getMessage()));
        app.exception(IllegalArgumentException.class, (e, ctx) -> sendError(ctx, 400, e.getMessage()));
        app.exception(IllegalStateException.class, (e, ctx) -> sendError(ctx, 409, e.getMessage()));
        app.exception(DateTimeParseException.class,
                (e, ctx) -> sendError(ctx, 400, "That start time is not a valid date and time."));
    }

    private static void sendError(Context ctx, int status, String message) {
        String text = (message == null || message.isBlank()) ? "Invalid request." : message;
        ctx.status(status).json(Map.of("message", text));
    }

    // ---------------------------------------------------------------
    // Who is asking?
    // ---------------------------------------------------------------
    private static User currentUser(Context ctx) {
        String id = ctx.sessionAttribute(SESSION_USER);
        return id == null ? null : users.get(id);
    }

    private static User requireUser(Context ctx) {
        User user = currentUser(ctx);
        if (user == null) {
            throw new AuthException(401, "Please log in.");
        }
        return user;
    }

    // Supervisors may manage any booking; students only their own.
    private static void requireOwnerOrSupervisor(User user, Booking booking) {
        boolean isOwner = booking.getStudent().getId().equals(user.getId());
        if (!isOwner && !user.canApprove()) {
            throw new AuthException(403, "You can only manage your own bookings.");
        }
    }

    private static User findByEmail(String email) {
        if (email == null) {
            return null;
        }
        String wanted = email.trim();
        for (User user : users.values()) {
            if (user.getEmail() != null && user.getEmail().equalsIgnoreCase(wanted)) {
                return user;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private static Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("role", user.getRole());
        map.put("canApprove", user.canApprove());
        map.put("maxActiveBookings", user.getMaxActiveBookings());
        if (user instanceof Student student) {
            LocalDateTime now = LocalDateTime.now();
            map.put("penaltyPoints", student.getPenaltyPoints());
            map.put("maxPenaltyPoints", Student.POINTS_BEFORE_SUSPENSION);
            map.put("suspended", student.isSuspended(now));
            map.put("suspendedUntil",
                    student.getSuspendedUntil() == null ? null : student.getSuspendedUntil().toString());
        }
        return map;
    }

    private static Map<String, Object> equipmentToMap(Equipment item, boolean forSupervisor) {
        LocalDateTime now = LocalDateTime.now();
        Booking holder = manager.findCheckedOut(item.getId());
        String status = !item.isAvailable() ? "OUT_OF_SERVICE" : (holder != null ? "IN_USE" : "AVAILABLE");

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId());
        map.put("name", item.getName());
        map.put("type", item.getType());
        map.put("location", item.getLocation());
        map.put("requiresApproval", item.isRequiresSupervisorApproval());
        map.put("maxHours", item.getMaxBookingHours());
        map.put("status", status);
        if (holder != null) {
            map.put("dueBack", holder.getEndTime().toString());
            map.put("overdue", holder.isOverdue(now));
            if (forSupervisor) {
                map.put("heldBy", holder.getStudent().getName());   // only supervisors see who has it
            }
        }
        return map;
    }

    private static Map<String, Object> bookingToMap(Booking b) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", b.getId());
        map.put("userId", b.getStudent().getId());
        map.put("userName", b.getStudent().getName());
        map.put("equipmentId", b.getEquipment().getId());
        map.put("equipmentName", b.getEquipment().getName());
        map.put("startTime", b.getStartTime().toString());
        map.put("endTime", b.getEndTime().toString());
        map.put("status", b.getStatus().name());
        map.put("requiresApproval", b.getEquipment().isRequiresSupervisorApproval());
        map.put("approved", b.isApprovedBySupervisor());
        map.put("checkedIn", b.isCheckedIn());
        LocalDateTime now = LocalDateTime.now();
        map.put("overdue", b.isOverdue(now));
        map.put("lateMinutes", b.minutesLate(now));
        return map;
    }

    private static synchronized void save() {
        try {
            bookingStore.saveBookings(manager.getAllBookings());
        } catch (IOException e) {
            throw new BookingException("Could not save data to the file.", e);
        }
    }

    private static synchronized void savePenalties() {
        try {
            penaltyStore.savePenalties(students.values());
        } catch (IOException e) {
            throw new BookingException("Could not save data to the file.", e);
        }
    }

    private static void loadSavedBookings() {
        try {
            penaltyStore.loadPenalties(students);
            int count = bookingStore.loadBookings(manager, students, equipment);
            System.out.println("Loaded " + count + " saved booking(s).");
        } catch (IOException e) {
            System.out.println("Could not read saved bookings: " + e.getMessage());
        }
    }

    private static void addUser(User user) {
        users.put(user.getId(), user);
        if (user instanceof Student student) {
            students.put(student.getId(), student);
        }
    }

    private static void seedData() {
        // Demo accounts for the project. Passwords are stored only as salted hashes.
        Student alice = new Student("S-100", "Alice", "alice@example.com", "2024001", "Computer Science");
        alice.setPassword("alice123");
        addUser(alice);

        Student mwansa = new Student("S-101", "Mwansa", "mwansa@example.com", "2024002", "Computer Science");
        mwansa.setPassword("mwansa123");
        addUser(mwansa);

        LabSupervisor smith = new LabSupervisor("L-200", "Mr. Smith", "smith@example.com", "EMP-77", "Main Lab");
        smith.setPassword("smith123");
        addUser(smith);

        List<Equipment> items = List.of(
                new Workstation("WS-01", "Dell Workstation 01", "Room A1", "WS-01"),
                new Workstation("WS-02", "Dell Workstation 02", "Room A1", "WS-02"),
                new SharedDevice("SD-03", "3D Printer", "Room B2", "3D Printer"),
                new SharedDevice("SD-04", "Laser Cutter", "Room B2", "Laser Cutter")
        );
        for (Equipment item : items) {
            equipment.put(item.getId(), item);
        }

        try {
            userStore.saveUsers(new ArrayList<>(users.values()));
        } catch (IOException e) {
            System.out.println("Could not save users: " + e.getMessage());
        }
    }
}
