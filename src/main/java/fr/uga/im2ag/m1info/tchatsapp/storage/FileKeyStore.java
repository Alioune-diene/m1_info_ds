package fr.uga.im2ag.m1info.tchatsapp.storage;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File-based implementation of {@link KeyStore} with encryption at rest.
 * <p>
 * Keys are stored encrypted using AES-256-GCM with a master key derived from a password
 * using PBKDF2. Each key file contains:
 * <ul>
 *   <li>Salt for PBKDF2 (16 bytes)</li>
 *   <li>IV for AES-GCM (12 bytes)</li>
 *   <li>Encrypted key data (variable)</li>
 *   <li>GCM authentication tag (16 bytes, appended by GCM)</li>
 * </ul>
 * <p>
 * File structure:
 * <pre>
 * ~/.tchatsapp/keys/{clientId}/
 *   ├── master.key           # Encrypted master key (optional, for password-less re-auth)
 *   ├── identity.keypair     # ECDH identity keypair
 *   ├── session_{convId}.key # Session keys per conversation
 *   └── metadata.json        # Key metadata (creation time, rotation count)
 * </pre>
 * <p>
 * Thread Safety: This class is thread-safe. File operations are synchronized
 * per-file using path-based locking.
 *
 * @see KeyStore
 */
public class FileKeyStore implements KeyStore {

    private static final Logger LOG = Logger.getLogger(FileKeyStore.class.getName());

    // ========================= Constants =========================

    /** Base directory name for key storage. */
    private static final String KEYS_DIR_NAME = ".tchatsapp/keys";

    /** File extension for session keys. */
    private static final String SESSION_KEY_EXTENSION = ".key";

    /** File prefix for session keys. */
    private static final String SESSION_KEY_PREFIX = "session_";

    /** File name for identity keypair. */
    private static final String IDENTITY_KEYPAIR_FILE = "identity.keypair";

    /** Algorithm for key derivation from password. */
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    /** Algorithm for key encryption. */
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    /** PBKDF2 iteration count (higher = more secure but slower). */
    private static final int PBKDF2_ITERATIONS = 100_000;

    /** Salt length in bytes. */
    private static final int SALT_LENGTH = 16;

    /** GCM IV length in bytes. */
    private static final int IV_LENGTH = 12;

    /** GCM tag length in bits. */
    private static final int GCM_TAG_LENGTH = 128;

    /** Derived key length in bits. */
    private static final int DERIVED_KEY_LENGTH = 256;

    /** Algorithm for ECDH keypair. */
    private static final String ECDH_ALGORITHM = "X25519";

    /** Crypto provider. */
    private static final String CRYPTO_PROVIDER = "BC";

    // ========================= Instance Fields =========================

    /** Base directory for this client's keys. */
    private final Path baseDir;

    /** The client ID. */
    private final int clientId;

    /** Master key for encrypting stored keys. Derived from password or generated. */
    private SecretKey masterKey;

    /** Secure random for generating salts and IVs. */
    private final SecureRandom secureRandom;

    /** Lock objects for file operations. */
    private final Map<Path, Object> fileLocks;

    // ========================= Constructors =========================

    /**
     * Creates a FileKeyStore for the specified client with a password-derived master key.
     *
     * @param clientId the client ID
     * @param password the password for key derivation
     * @throws IllegalArgumentException if clientId is not positive or password is null/empty
     * @throws IOException if the storage directory cannot be created
     */
    public FileKeyStore(int clientId, char[] password) throws IOException {
        if (clientId <= 0) {
            throw new IllegalArgumentException("Client ID must be positive: " + clientId);
        }
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        this.clientId = clientId;
        this.secureRandom = new SecureRandom();
        this.fileLocks = new HashMap<>();
        this.baseDir = resolveBaseDir(clientId);

        ensureDirectoryExists();
        initializeMasterKey(password);
    }

    /**
     * Creates a FileKeyStore with an auto-generated master key.
     * <p>
     * Warning: Without a password, the master key is stored in a file.
     * This is less secure but allows password-less operation.
     *
     * @param clientId the client ID
     * @throws IOException if the storage directory cannot be created
     */
    public FileKeyStore(int clientId) throws IOException {
        if (clientId <= 0) {
            throw new IllegalArgumentException("Client ID must be positive: " + clientId);
        }

        this.clientId = clientId;
        this.secureRandom = new SecureRandom();
        this.fileLocks = new HashMap<>();
        this.baseDir = resolveBaseDir(clientId);

        ensureDirectoryExists();
        initializeAutoMasterKey();
    }

    // ========================= KeyStore Implementation =========================

    @Override
    public void saveSessionKey(String conversationId, SecretKey key) throws IOException {
        Objects.requireNonNull(conversationId, "Conversation ID cannot be null");
        Objects.requireNonNull(key, "Key cannot be null");

        Path keyFile = getSessionKeyPath(conversationId);
        byte[] encryptedData = encryptKey(key.getEncoded());

        writeFileAtomic(keyFile, encryptedData);
        LOG.fine("Saved session key for conversation: " + conversationId);
    }

    @Override
    public SecretKey loadSessionKey(String conversationId) throws IOException {
        Objects.requireNonNull(conversationId, "Conversation ID cannot be null");

        Path keyFile = getSessionKeyPath(conversationId);

        if (!Files.exists(keyFile)) {
            return null;
        }

        byte[] encryptedData = Files.readAllBytes(keyFile);
        byte[] keyBytes = decryptKey(encryptedData);

        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        Arrays.fill(keyBytes, (byte) 0);

        LOG.fine("Loaded session key for conversation: " + conversationId);
        return key;
    }

    @Override
    public void deleteSessionKey(String conversationId) throws IOException {
        Objects.requireNonNull(conversationId, "Conversation ID cannot be null");

        Path keyFile = getSessionKeyPath(conversationId);

        if (Files.exists(keyFile)) {
            secureDelete(keyFile);
            LOG.fine("Deleted session key for conversation: " + conversationId);
        }
    }

    @Override
    public Map<String, SecretKey> loadAllSessionKeys() throws IOException {
        Map<String, SecretKey> sessionKeys = new HashMap<>();

        Set<String> conversationIds = listConversationIds();
        for (String convId : conversationIds) {
            SecretKey key = loadSessionKey(convId);
            if (key != null) {
                sessionKeys.put(convId, key);
            }
        }

        return sessionKeys;
    }

    // ========================= Identity Keypair Operations =========================

    /**
     * Saves the client's ECDH identity keypair.
     *
     * @param keyPair the keypair to save
     * @throws IOException if saving fails
     */
    public void saveIdentityKeyPair(KeyPair keyPair) throws IOException {
        Objects.requireNonNull(keyPair, "KeyPair cannot be null");

        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();

        // Format: [4 bytes private length][private key][public key]
        ByteBuffer buffer = ByteBuffer.allocate(4 + privateKeyBytes.length + publicKeyBytes.length);
        buffer.putInt(privateKeyBytes.length);
        buffer.put(privateKeyBytes);
        buffer.put(publicKeyBytes);

        byte[] encryptedData = encryptKey(buffer.array());

        Path keyPairFile = baseDir.resolve(IDENTITY_KEYPAIR_FILE);
        writeFileAtomic(keyPairFile, encryptedData);

        Arrays.fill(privateKeyBytes, (byte) 0);
        Arrays.fill(buffer.array(), (byte) 0);

        LOG.info("Saved identity keypair for client " + clientId);
    }

    /**
     * Loads the client's ECDH identity keypair.
     *
     * @return the keypair, or null if not found
     * @throws IOException if loading fails
     */
    public KeyPair loadIdentityKeyPair() throws IOException {
        Path keyPairFile = baseDir.resolve(IDENTITY_KEYPAIR_FILE);

        if (!Files.exists(keyPairFile)) {
            return null;
        }

        byte[] encryptedData = Files.readAllBytes(keyPairFile);
        byte[] decryptedData = decryptKey(encryptedData);

        try {
            ByteBuffer buffer = ByteBuffer.wrap(decryptedData);
            int privateKeyLength = buffer.getInt();

            byte[] privateKeyBytes = new byte[privateKeyLength];
            buffer.get(privateKeyBytes);

            byte[] publicKeyBytes = new byte[buffer.remaining()];
            buffer.get(publicKeyBytes);

            KeyFactory keyFactory = KeyFactory.getInstance(ECDH_ALGORITHM, CRYPTO_PROVIDER);

            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            Arrays.fill(privateKeyBytes, (byte) 0);
            Arrays.fill(decryptedData, (byte) 0);

            LOG.info("Loaded identity keypair for client " + clientId);
            return new KeyPair(publicKey, privateKey);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to reconstruct identity keypair", e);
        }
    }

    /**
     * Deletes the client's identity keypair.
     *
     * @throws IOException if deletion fails
     */
    public void deleteIdentityKeyPair() throws IOException {
        Path keyPairFile = baseDir.resolve(IDENTITY_KEYPAIR_FILE);
        if (Files.exists(keyPairFile)) {
            secureDelete(keyPairFile);
            LOG.info("Deleted identity keypair for client " + clientId);
        }
    }

    // ========================= Listing Operations =========================

    /**
     * Lists all stored conversation IDs.
     *
     * @return set of conversation IDs with stored session keys
     * @throws IOException if listing fails
     */
    public Set<String> listConversationIds() throws IOException {
        if (!Files.exists(baseDir)) {
            return Collections.emptySet();
        }

        try (Stream<Path> files = Files.list(baseDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.startsWith(SESSION_KEY_PREFIX) && name.endsWith(SESSION_KEY_EXTENSION))
                    .map(name -> name.substring( SESSION_KEY_PREFIX.length(), name.length() - SESSION_KEY_EXTENSION.length()))
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Checks if a session key exists for the given conversation.
     *
     * @param conversationId the conversation ID
     * @return true if a session key exists
     */
    public boolean hasSessionKey(String conversationId) {
        return Files.exists(getSessionKeyPath(conversationId));
    }

    /**
     * Checks if an identity keypair exists.
     *
     * @return true if identity keypair exists
     */
    public boolean hasIdentityKeyPair() {
        return Files.exists(baseDir.resolve(IDENTITY_KEYPAIR_FILE));
    }

    // ========================= Maintenance Operations =========================

    /**
     * Deletes all stored keys for this client.
     * <p>
     * Warning: This operation is irreversible!
     *
     * @throws IOException if deletion fails
     */
    public void deleteAllKeys() throws IOException {
        if (!Files.exists(baseDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(baseDir)) {
            for (Path file : files.toList()) {
                secureDelete(file);
            }
        }

        Files.deleteIfExists(baseDir);
        LOG.warning("Deleted all keys for client " + clientId);
    }

    // ========================= Private: Master Key Management =========================

    /**
     * Initializes the master key from a password.
     */
    private void initializeMasterKey(char[] password) throws IOException {
        Path saltFile = baseDir.resolve(".salt");
        byte[] salt;

        if (Files.exists(saltFile)) {
            salt = Files.readAllBytes(saltFile);
        } else {
            salt = new byte[SALT_LENGTH];
            secureRandom.nextBytes(salt);
            Files.write(saltFile, salt);
        }

        try {
            this.masterKey = deriveKeyFromPassword(password, salt);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to derive master key from password", e);
        }
    }

    /**
     * Initializes an auto-generated master key.
     */
    private void initializeAutoMasterKey() throws IOException {
        Path masterKeyFile = baseDir.resolve(".masterkey");

        if (Files.exists(masterKeyFile)) {
            // Load existing master key (stored in plaintext - less secure)
            byte[] keyBytes = Files.readAllBytes(masterKeyFile);
            this.masterKey = new SecretKeySpec(keyBytes, "AES");
            Arrays.fill(keyBytes, (byte) 0);
        } else {
            // Generate new master key
            byte[] keyBytes = new byte[32];
            secureRandom.nextBytes(keyBytes);
            this.masterKey = new SecretKeySpec(keyBytes, "AES");

            // Store it (warning: plaintext storage)
            Files.write(masterKeyFile, keyBytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            Arrays.fill(keyBytes, (byte) 0);

            LOG.warning("Auto-generated master key created and stored in plaintext. " +
                    "This is less secure than using a password.");
        }
    }

    /**
     * Derives a key from a password using PBKDF2.
     */
    private SecretKey deriveKeyFromPassword(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, DERIVED_KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    // ========================= Private: Encryption/Decryption =========================

    /**
     * Encrypts key data with the master key.
     */
    private byte[] encryptKey(byte[] data) throws IOException {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec);

            byte[] encrypted = cipher.doFinal(data);

            // Prepend IV to encrypted data
            ByteBuffer buffer = ByteBuffer.allocate(IV_LENGTH + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);

            return buffer.array();

        } catch (GeneralSecurityException e) {
            throw new IOException("Encryption failed", e);
        }
    }

    /**
     * Decrypts key data with the master key.
     */
    private byte[] decryptKey(byte[] encryptedData) throws IOException {
        if (encryptedData.length < IV_LENGTH) {
            throw new IOException("Invalid encrypted data: too short");
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec);

            return cipher.doFinal(encrypted);

        } catch (GeneralSecurityException e) {
            throw new IOException("Decryption failed (wrong password or corrupted data?)", e);
        }
    }

    // ========================= Private: File Operations =========================

    /**
     * Resolves the base directory for the client.
     */
    private static Path resolveBaseDir(int clientId) {
        return Paths.get(System.getProperty("user.home"), KEYS_DIR_NAME, String.valueOf(clientId));
    }

    /**
     * Ensures the storage directory exists.
     */
    private void ensureDirectoryExists() throws IOException {
        Files.createDirectories(baseDir);

        // Set restrictive permissions (Unix-like systems)
        try {
            Set<java.nio.file.attribute.PosixFilePermission> perms = java.nio.file.attribute.PosixFilePermissions.fromString("rwx------");
            Files.setPosixFilePermissions(baseDir, perms);
        } catch (UnsupportedOperationException e) {
            LOG.fine("POSIX permissions not supported on this platform");
        }
    }

    /**
     * Gets the path for a session key file.
     */
    private Path getSessionKeyPath(String conversationId) {
        return baseDir.resolve(SESSION_KEY_PREFIX + sanitize(conversationId) + SESSION_KEY_EXTENSION);
    }

    /**
     * Sanitizes a conversation ID for use as a filename.
     */
    private String sanitize(String conversationId) {
        return conversationId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Writes data to a file atomically (write to temp, then rename).
     */
    private void writeFileAtomic(Path target, byte[] data) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");

        synchronized (getFileLock(target)) {
            try {
                Files.write(temp, data,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    /**
     * Securely deletes a file by overwriting with random data before deletion.
     */
    private void secureDelete(Path file) throws IOException {
        synchronized (getFileLock(file)) {
            if (!Files.exists(file)) {
                return;
            }

            long size = Files.size(file);
            byte[] random = new byte[(int) Math.min(size, 8192)];

            // Overwrite 3 times with random data
            for (int i = 0; i < 3; i++) {
                secureRandom.nextBytes(random);
                Files.write(file, random, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }

            Files.delete(file);
        }
    }

    /**
     * Gets or creates a lock object for a file path.
     */
    private Object getFileLock(Path path) {
        synchronized (fileLocks) {
            return fileLocks.computeIfAbsent(path.toAbsolutePath(), k -> new Object());
        }
    }

    // ========================= Getters =========================

    /**
     * Gets the client ID.
     *
     * @return the client ID
     */
    public int getClientId() {
        return clientId;
    }

    /**
     * Gets the base directory path.
     *
     * @return the base directory
     */
    public Path getBaseDir() {
        return baseDir;
    }
}