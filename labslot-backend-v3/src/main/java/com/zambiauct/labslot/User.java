package com.zambiauct.labslot;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public abstract class User {
    private final String id;
    private String name;
    private String email;

    // The password itself is never stored, only a salted hash of it.
    private byte[] passwordSalt;
    private byte[] passwordHash;

    protected User(String id, String name, String email) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }

    public void setPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        passwordSalt = new byte[16];
        new SecureRandom().nextBytes(passwordSalt);
        passwordHash = hash(plainPassword, passwordSalt);
    }

    public boolean checkPassword(String plainPassword) {
        if (plainPassword == null || passwordHash == null) {
            return false;
        }
        return MessageDigest.isEqual(passwordHash, hash(plainPassword, passwordSalt));
    }

    private static byte[] hash(String plainPassword, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(plainPassword.toCharArray(), salt, 65_536, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not hash the password", e);
        }
    }

    // Each subclass must define these
    public abstract String getRole();
    public abstract int getMaxActiveBookings();
    public abstract boolean canApprove();

    @Override
    public String toString() {
        return getRole() + " [" + id + "] " + name;
    }
}