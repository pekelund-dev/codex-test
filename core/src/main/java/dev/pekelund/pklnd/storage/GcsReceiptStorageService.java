package dev.pekelund.pklnd.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

@Service
@ConditionalOnBean(Storage.class)
public class GcsReceiptStorageService implements ReceiptStorageService {

    private static final DateTimeFormatter OBJECT_PREFIX =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.US).withZone(ZoneOffset.UTC);
    private static final int MAX_OBJECT_FILENAME_LENGTH = 60;
    private static final String CONTENT_HASH_METADATA_KEY = "content-sha256";

    private final Storage storage;
    private final GcsProperties properties;

    public GcsReceiptStorageService(Storage storage, GcsProperties properties) {
        this.storage = storage;
        this.properties = properties;
        Assert.isTrue(StringUtils.hasText(properties.getBucket()),
            "gcs.bucket must be configured when Google Cloud Storage is enabled");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<ReceiptFile> listReceipts() {
        try {
            Iterable<Blob> blobs = storage.list(properties.getBucket()).iterateAll();
            List<ReceiptFile> files = new ArrayList<>();
            for (Blob blob : blobs) {
                if (blob.isDirectory()) {
                    continue;
                }
                ReceiptOwner owner = ReceiptOwner.fromMetadata(blob.getMetadata());
                OffsetDateTime updateTime = blob.getUpdateTimeOffsetDateTime();
                Instant updated = updateTime != null ? updateTime.toInstant() : null;
                files.add(new ReceiptFile(blob.getName(), blob.getSize(), updated,
                    blob.getContentType(), owner));
            }
            files.sort((left, right) -> {
                Instant leftUpdated = left.updated();
                Instant rightUpdated = right.updated();
                if (leftUpdated == null && rightUpdated == null) {
                    return left.name().compareToIgnoreCase(right.name());
                }
                if (leftUpdated == null) {
                    return 1;
                }
                if (rightUpdated == null) {
                    return -1;
                }
                return rightUpdated.compareTo(leftUpdated);
            });
            return files;
        } catch (StorageException ex) {
            throw new ReceiptStorageException("Unable to list receipt files", ex);
        }
    }

    @Override
    public List<StoredReceiptReference> uploadFiles(List<MultipartFile> files, ReceiptOwner owner) {
        List<StoredReceiptReference> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String originalFilename = file.getOriginalFilename();
            
            // Read file content once for both hash calculation and upload
            byte[] fileContent;
            try (InputStream inputStream = file.getInputStream()) {
                fileContent = readAllBytes(inputStream);
            } catch (IOException ex) {
                String displayName = StringUtils.hasText(originalFilename) ? originalFilename : "file";
                throw new ReceiptStorageException("Failed to read file '%s'".formatted(displayName), ex);
            }
            
            // Calculate content hash
            String contentHash = calculateSha256Hash(fileContent);
            
            // Check for duplicates
            ReceiptFile existingReceipt = findReceiptByContentHash(contentHash, owner);
            if (existingReceipt != null) {
                String displayName = StringUtils.hasText(originalFilename) ? originalFilename : "file";
                throw new DuplicateReceiptException(
                    "Kvittot '%s' har redan laddats upp tidigare.".formatted(displayName),
                    existingReceipt.name(),
                    contentHash
                );
            }

            String objectName = buildObjectName(originalFilename);
            Map<String, String> metadata = new HashMap<>(owner != null && owner.hasValues() ? owner.toMetadata() : Map.of());
            metadata.put(CONTENT_HASH_METADATA_KEY, contentHash);
            
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.getBucket(), objectName))
                .setContentType(file.getContentType())
                .setMetadata(metadata)
                .build();

            try {
                storage.create(blobInfo, fileContent);
                uploaded.add(new StoredReceiptReference(properties.getBucket(), objectName, owner));
            } catch (StorageException ex) {
                String displayName = StringUtils.hasText(originalFilename) ? originalFilename : objectName;
                throw new ReceiptStorageException("Failed to upload file '%s'".formatted(displayName), ex);
            }
        }

        return List.copyOf(uploaded);
    }

    @Override
    public void deleteReceiptsForOwner(ReceiptOwner owner) {
        if (owner == null) {
            return;
        }

        try {
            Iterable<Blob> blobs = storage.list(properties.getBucket()).iterateAll();
            for (Blob blob : blobs) {
                if (blob.isDirectory()) {
                    continue;
                }
                ReceiptOwner fileOwner = ReceiptOwner.fromMetadata(blob.getMetadata());
                if (!ReceiptOwnerMatcher.belongsToCurrentOwner(fileOwner, owner)) {
                    continue;
                }
                storage.delete(blob.getBlobId());
            }
        } catch (StorageException ex) {
            throw new ReceiptStorageException("Unable to delete receipt files", ex);
        }
    }

    private String buildObjectName(String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename : "receipt";
        filename = extractFilename(filename);
        filename = shortenFilename(filename, MAX_OBJECT_FILENAME_LENGTH);
        filename = encodeFilename(filename);
        if (!StringUtils.hasText(filename)) {
            filename = "receipt";
        }
        String prefix = OBJECT_PREFIX.format(Instant.now());
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return prefix + "_" + suffix + "_" + filename;
    }

    private String extractFilename(String filename) {
        try {
            Path path = Paths.get(filename);
            Path fileName = path.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        } catch (InvalidPathException ex) {
            // Fall back to manual parsing below when the path contains invalid characters.
        }
        int separatorIndex = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (separatorIndex >= 0 && separatorIndex < filename.length() - 1) {
            return filename.substring(separatorIndex + 1);
        }
        return filename;
    }

    private String encodeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return filename;
        }
        return UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8);
    }

    private String shortenFilename(String filename, int maxLength) {
        if (!StringUtils.hasText(filename) || filename.length() <= maxLength) {
            return filename;
        }

        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex > 0 && extensionIndex < filename.length() - 1) {
            String baseName = filename.substring(0, extensionIndex);
            String extension = filename.substring(extensionIndex);
            int allowedBaseLength = Math.max(1, maxLength - extension.length() - 1);
            if (baseName.length() > allowedBaseLength) {
                baseName = baseName.substring(0, allowedBaseLength);
            }
            return baseName + "…" + extension;
        }

        int safeLength = Math.max(1, maxLength - 1);
        return filename.substring(0, safeLength) + "…";
    }

    private String calculateSha256Hash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new ReceiptStorageException("SHA-256 algorithm not available", ex);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private ReceiptFile findReceiptByContentHash(String contentHash, ReceiptOwner owner) {
        if (!StringUtils.hasText(contentHash)) {
            return null;
        }

        try {
            Iterable<Blob> blobs = storage.list(properties.getBucket()).iterateAll();
            for (Blob blob : blobs) {
                if (blob.isDirectory()) {
                    continue;
                }
                
                Map<String, String> metadata = blob.getMetadata();
                if (metadata == null) {
                    continue;
                }
                
                String blobHash = metadata.get(CONTENT_HASH_METADATA_KEY);
                if (!contentHash.equals(blobHash)) {
                    continue;
                }
                
                // Check if this blob belongs to the same owner (or any owner if owner is null)
                ReceiptOwner blobOwner = ReceiptOwner.fromMetadata(metadata);
                if (owner != null && !ReceiptOwnerMatcher.belongsToCurrentOwner(blobOwner, owner)) {
                    continue;
                }
                
                // Found a duplicate
                OffsetDateTime updateTime = blob.getUpdateTimeOffsetDateTime();
                Instant updated = updateTime != null ? updateTime.toInstant() : null;
                return new ReceiptFile(blob.getName(), blob.getSize(), updated, blob.getContentType(), blobOwner);
            }
            return null;
        } catch (StorageException ex) {
            throw new ReceiptStorageException("Unable to check for duplicate receipts", ex);
        }
    }
}

