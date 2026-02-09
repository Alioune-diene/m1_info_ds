package fr.uga.im2ag.m1info.tchatsapp.common.repository;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * A generic repository writer that handles serialization and deserialization of data to and from a file.
 *
 * @param <T> the type of data to be stored in the repository
 */
// TODO: Find a way to reduce I/O operations for performance improvement
public class RepositoryWriter<T> {
    private static final Logger LOG = Logger.getLogger(RepositoryWriter.class.getName());
    private final String filePath;

    public RepositoryWriter(String filePath) {
        Path folder = null;
        String fp = System.getProperty("user.dir");
        try {
            folder = Files.createTempDirectory("tchatsapp");
            fp = folder.toAbsolutePath().toString();
        } catch (IOException e) {
            LOG.severe("Failed to create temporary directory: " + e.getMessage());
        } finally {
            this.filePath = fp + File.separator + filePath + ".dat";
            LOG.info("RepositoryWriter initialized with file path: " + this.filePath);
        }
    }

    public void writeData(Set<T> dataSet) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(dataSet);
        } catch (IOException e) {
            LOG.severe("Failed to write data to file: " + e.getMessage());
        }
    }

    public void writeData(T data) {
        Set<T> existingData = readData();
        existingData.add(data);
        writeData(existingData);
    }

    @SuppressWarnings("unchecked")
    public Set<T> readData() {
        File file = new File(filePath);
        if (!file.exists()) {
            return new HashSet<T>();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            Object obj = ois.readObject();
            if (obj instanceof Set) {
                return (Set<T>) obj;
            }
            return new HashSet<T>();
        } catch (IOException | ClassNotFoundException e) {
            LOG.severe("Failed to read data from file: " + e.getMessage());
            return new HashSet<T>();
        }
    }

    public T readData(Predicate<T> condition) {
        Set<T> allData = readData();
        return allData.stream()
                .filter(condition)
                .findFirst()
                .orElse(null);
    }

    public void updateData(Predicate<T> condition, T newData) {
        Set<T> allData = readData();
        
        T toRemove = null;
        for (T item : allData) {
            if (condition.test(item)) {
                toRemove = item;
                break;
            }
        }
        
        if (toRemove != null) {
            allData.remove(toRemove);
            allData.add(newData);
            writeData(allData);
        }
    }

    public void removeData(Predicate<T> condition) {
        Set<T> allData = readData();
        boolean removed = allData.removeIf(condition);
        
        if (removed) {
            writeData(allData);
        }
    }

    public boolean deleteCache() {
        File file = new File(filePath);
        return file.exists() && file.delete();
    }
}
