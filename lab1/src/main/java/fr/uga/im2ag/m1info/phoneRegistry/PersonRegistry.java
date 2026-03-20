package fr.uga.im2ag.m1info.phoneRegistry;

import java.util.List;

public class PersonRegistry {
    private static final List<Person> persons = new java.util.ArrayList<>();

    public static void addPerson(Person person) {
        persons.add(person);
    }

    public static String getPhone(String name) {
        Person person = search(name);
        return (person != null) ? person.getPhoneNumber() : null;
    }

    public static Iterable<Person> getAllPersons() {
        return new java.util.ArrayList<>(persons);
    }

    public static Person search(String name) {
        for (Person person : persons) {
            if (person.getName().equalsIgnoreCase(name)) {
                return person;
            }
        }
        return null;
    }
}

