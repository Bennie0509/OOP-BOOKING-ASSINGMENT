# LabSlot

LabSlot is a website where students at Zambia University College of Technology book lab
equipment (computers, 3D printers) instead of using a notebook or WhatsApp.

- A student picks a machine and a time.
- Restricted equipment needs a supervisor's approval.
- The student checks in on arrival; no-shows expire after 15 minutes.
- Double bookings are blocked.
- Supervisors see how many devices are available right now, who has each one, and what is overdue.
- A student cannot cancel once the item is checked out. It must be handed back and a supervisor confirms the return.
- Late returns earn penalty points. 3 points suspend booking for 7 days (see the constants at the top of `Student.java`).
- Everything is saved to a file (`data/`) so it survives a restart.

## How to run

You need JDK 21 and Maven installed.

```
mvn compile exec:java
```

Then open http://localhost:7070 in your browser and log in.

Demo accounts:

| Role | Email | Password |
|---|---|---|
| Student | alice@example.com | alice123 |
| Student | mwansa@example.com | mwansa123 |
| Supervisor | smith@example.com | smith123 |

Passwords are never stored. Each user keeps only a salted PBKDF2 hash. The server remembers who is
logged in with a session, so the page cannot pretend to be someone else.

## Classes

| Class | Responsibility |
|---|---|
| User (abstract) | Shared id, name and email, plus a private password hash with `setPassword()` and `checkPassword()`; `canApprove()` and `getMaxActiveBookings()` differ per subclass |
| Student | A user who books equipment; keeps penalty points and any suspension |
| LabSupervisor | A user who can approve restricted bookings |
| Equipment (abstract) | Shared equipment fields; booking rules differ per subclass |
| Workstation | Computer with session time limits |
| SharedDevice | Shared device that needs supervisor approval |
| Booking | One reservation linking a user and equipment; knows if it is overdue |
| BookingStatus | Enum: PENDING, ACTIVE, COMPLETED, EXPIRED |
| BookingManager | Holds bookings, blocks conflicts, expires no-shows, confirms returns and applies penalties |
| BookingException | Custom exception for booking errors |
| LabDataStore | Reads and writes users, bookings and penalties to files in `data/` |
| LabSlotApplication | Starts Javalin and defines the endpoints |

## Endpoints

| Method | Path | What it does |
|---|---|---|
| POST | /api/login | Log in with email and password |
| POST | /api/logout | Log out |
| GET | /api/me | The logged-in user |
| GET | /api/equipment | List equipment with live status (available, in use) |
| GET | /api/bookings | Supervisors see all bookings, students see their own |
| POST | /api/bookings | Student creates a booking for themselves |
| PUT | /api/bookings/{id}/approve | Supervisor approves (decided by `canApprove()`) |
| PUT | /api/bookings/{id}/checkin | Owner or supervisor checks in |
| PUT | /api/bookings/{id}/complete | Supervisor confirms the item was returned (adds a penalty if late) |
| DELETE | /api/bookings/{id} | Owner or supervisor cancels, only before check-in |

## Team

| Full Name | Student Number |
|-----------|----------------|
| benny simpamba | 2410092 |
| alfred sichivula | 2410025 |
| genesis funga | 2410290 |
| angel mugala | 24 |
