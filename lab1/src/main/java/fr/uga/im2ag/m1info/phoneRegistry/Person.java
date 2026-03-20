package fr.uga.im2ag.m1info.phoneRegistry;

public class Person {
    private final String name;
    private final String firstName;
    private final String phoneNumber;

    public Person(String name, String firstName, String phoneNumber) {
        this.name = name;
        this.firstName = firstName;
        this.phoneNumber = phoneNumber;
    }

    public String getName() {
        return name;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String serialize() {
        return name + ";" + firstName + ";" + phoneNumber;
    }

    public static Person deserialize(String data) {
        String[] parts = data.split(";");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid data format");
        }
        return new Person(parts[0], parts[1], parts[2]);
    }
}