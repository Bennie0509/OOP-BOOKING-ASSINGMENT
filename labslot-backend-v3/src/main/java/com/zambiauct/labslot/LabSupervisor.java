package com.zambiauct.labslot;

public class LabSupervisor extends User {
    private final String employeeNumber;
    private String labName;

    public LabSupervisor(String id, String name, String email,
                         String employeeNumber, String labName) {
        super(id, name, email);
        this.employeeNumber = employeeNumber;
        this.labName = labName;
    }

    public String getEmployeeNumber() { return employeeNumber; }
    public String getLabName() { return labName; }
    public void setLabName(String labName) { this.labName = labName; }

    @Override
    public String getRole() { return "LAB_SUPERVISOR"; }

    @Override
    public int getMaxActiveBookings() { return 10; }

    @Override
    public boolean canApprove() { return true; }
}