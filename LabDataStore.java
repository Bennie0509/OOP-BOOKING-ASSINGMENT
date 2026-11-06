package com.zambiauct.labslot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class LabDataStore {
    private final Path storageFile;

    public LabDataStore(String fileName) {
        this.storageFile = Paths.get(fileName);
    }

    public void saveUsers(List<User> users) throws IOException {
        List<String> lines = new ArrayList<>();
        for (User user : users) {
            lines.add(user.getId() + "," + user.getName() + "," + user.getEmail() + "," + user.getRole());
        }
        writeLines(lines);
    }

    public List<String> loadUsers() throws IOException {
        if (Files.notExists(storageFile)) {
            return new ArrayList<>();
        }
        return Files.readAllLines(storageFile);
    }

    // One line per booking: id,studentId,equipmentId,start,end,status,approved,checkedIn
    public void saveBookings(List<Booking> bookings) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Booking b : bookings) {
            lines.add(String.join(",",
                    b.getId(),
                    b.getStudent().getId(),
                    b.getEquipment().getId(),
                    b.getStartTime().toString(),
                    b.getEndTime().toString(),
                    b.getStatus().name(),
                    String.valueOf(b.isApprovedBySupervisor()),
                    String.valueOf(b.isCheckedIn())));
        }
        writeLines(lines);
    }

    // Rebuilds saved bookings and hands them to the manager. Returns how many were loaded.
    public int loadBookings(BookingManager manager,
                            Map<String, Student> students,
                            Map<String, Equipment> equipment) throws IOException {
        if (Files.notExists(storageFile)) {
            return 0;
        }
        int loaded = 0;
        for (String line : Files.readAllLines(storageFile)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                String[] p = line.split(",");
                Student student = students.get(p[1]);
                Equipment item = equipment.get(p[2]);
                if (student == null || item == null) {
                    continue;
                }
                Booking booking = new Booking(p[0], student, item,
                        LocalDateTime.parse(p[3]), LocalDateTime.parse(p[4]));
                if (Boolean.parseBoolean(p[6])) {
                    booking.approveBySupervisor();
                }
                if (Boolean.parseBoolean(p[7])) {
                    booking.checkIn();
                }
                BookingStatus status = BookingStatus.valueOf(p[5]);
                if (status == BookingStatus.COMPLETED) {
                    booking.complete();
                } else if (status == BookingStatus.EXPIRED) {
                    booking.expire();
                }
                manager.restoreBooking(booking);
                loaded++;
            } catch (RuntimeException badLine) {
                System.out.println("Skipped a damaged line in " + storageFile + ": " + line);
            }
        }
        return loaded;
    }

    // One line per student: studentId,penaltyPoints,suspendedUntil (empty if not suspended)
    public void savePenalties(Collection<Student> students) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Student s : students) {
            String until = s.getSuspendedUntil() == null ? "" : s.getSuspendedUntil().toString();
            lines.add(s.getId() + "," + s.getPenaltyPoints() + "," + until);
        }
        writeLines(lines);
    }

    public void loadPenalties(Map<String, Student> students) throws IOException {
        if (Files.notExists(storageFile)) {
            return;
        }
        for (String line : Files.readAllLines(storageFile)) {
            try {
                String[] p = line.split(",", -1);
                Student student = students.get(p[0]);
                if (student != null) {
                    LocalDateTime until = p[2].isBlank() ? null : LocalDateTime.parse(p[2]);
                    student.restorePenalties(Integer.parseInt(p[1]), until);
                }
            } catch (RuntimeException badLine) {
                System.out.println("Skipped a damaged line in " + storageFile + ": " + line);
            }
        }
    }

    private void writeLines(List<String> lines) throws IOException {
        Path parent = storageFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(storageFile, lines);
    }
}
