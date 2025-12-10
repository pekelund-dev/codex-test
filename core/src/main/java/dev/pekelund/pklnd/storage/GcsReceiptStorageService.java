package dev.pekelund.pklnd.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

@Service
@ConditionalOnBean(Storage.class)
public class GcsReceiptStorageService implements ReceiptStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GcsReceiptStorageService.class);
    private static final DateTimeFormatter OBJECT_PREFIX =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.US).withZone(ZoneOffset.UTC);
    private static final int MAX_OBJECT_FILENAME_LENGTH = 60;
    private static final String CONTENT_HASH_METADATA_KEY = "content-sha256";
    private static final String HASH_INDEX_PREFIX = ".receipt-hashes/";
    private static final int HASH_PREFIX_LENGTH = 4;

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
                // Skip hash index entries - these are internal implementation details
                if (blob.getName().startsWith(HASH_INDEX_PREFIX)) {
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
                fileContent = inputStream.readAllBytes();
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
                
                // Create hash index entry for efficient duplicate detection
                // If this fails, rollback the receipt upload to maintain consistency
                try {
                    createHashIndexEntry(contentHash, objectName, owner);
                } catch (ReceiptStorageException indexEx) {
                    // Rollback: delete the receipt we just uploaded
                    try {
                        storage.delete(BlobId.of(properties.getBucket(), objectName));
                        LOGGER.warn("Rolled back receipt upload for {} due to index creation failure", objectName);
                    } catch (StorageException deleteEx) {
                        LOGGER.error("Failed to rollback receipt {} after index creation failure", objectName, deleteEx);
                    }
                    throw indexEx;
                }
                
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
                
                // Delete hash index entry if this is a receipt file
                if (!blob.getName().startsWith(HASH_INDEX_PREFIX)) {
                    deleteHashIndexEntry(blob);
                }
                
                // Delete the blob itself
                storage.delete(blob.getBlobId());
            }
        } catch (StorageException ex) {
            throw new ReceiptStorageException("Unable to delete receipt files", ex);
        }
    }
    
    private void deleteHashIndexEntry(Blob receiptBlob) {
        try {
            Map<String, String> metadata = receiptBlob.getMetadata();
            if (metadata == null) {
                return;
            }
            
            String contentHash = metadata.get(CONTENT_HASH_METADATA_KEY);
            // SHA-256 produces 64-character hex string; validate proper length and format
            if (!isValidSha256Hash(contentHash)) {
                LOGGER.debug("Skipping hash index cleanup for {}: invalid or missing hash", receiptBlob.getName());
                return;
            }
            
            String hashPrefix = contentHash.substring(0, HASH_PREFIX_LENGTH);
            String indexPath = HASH_INDEX_PREFIX + hashPrefix + "/" + contentHash;
            
            BlobId indexBlobId = BlobId.of(properties.getBucket(), indexPath);
            storage.delete(indexBlobId);
        } catch (StorageException ex) {
            // Log but don't fail the delete operation
            LOGGER.warn("Failed to delete hash index entry for {}: {}", receiptBlob.getName(), ex.getMessage());
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

    String calculateSha256Hash(byte[] content) {
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

    private boolean isValidSha256Hash(String hash) {
        if (hash == null || hash.length() != 64) {
            return false;
        }
        // Validate that hash contains only hexadecimal characters [0-9a-f]
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private ReceiptFile findReceiptByContentHash(String contentHash, ReceiptOwner owner) {
        if (!StringUtils.hasText(contentHash)) {
            return null;
        }

        // SHA-256 produces 64-character hex string; validate proper length and format
        if (!isValidSha256Hash(contentHash)) {
            LOGGER.warn("Invalid hash format for duplicate check: {}", contentHash);
            return null;
        }

        try {
            // Use hash prefix-based lookup for efficient querying
            // Only scan blobs under the specific hash prefix instead of entire bucket
            String hashPrefix = HASH_INDEX_PREFIX + contentHash.substring(0, HASH_PREFIX_LENGTH) + "/";
            
            Iterable<Blob> indexBlobs = storage.list(
                properties.getBucket(),
                Storage.BlobListOption.prefix(hashPrefix)
            ).iterateAll();
            
            for (Blob indexBlob : indexBlobs) {
                if (indexBlob.isDirectory()) {
                    continue;
                }
                
                Map<String, String> metadata = indexBlob.getMetadata();
                if (metadata == null) {
                    continue;
                }
                
                String blobHash = metadata.get(CONTENT_HASH_METADATA_KEY);
                if (!contentHash.equals(blobHash)) {
                    continue;
                }
                
                // Check if this blob belongs to the same owner
                ReceiptOwner blobOwner = ReceiptOwner.fromMetadata(metadata);
                if (owner != null && !ReceiptOwnerMatcher.belongsToCurrentOwner(blobOwner, owner)) {
                    continue;
                }
                
                // Found a duplicate - fetch the actual receipt file metadata
                String actualObjectName = metadata.get("receipt-object-name");
                if (StringUtils.hasText(actualObjectName)) {
                    Blob actualBlob = storage.get(properties.getBucket(), actualObjectName);
                    if (actualBlob != null) {
                        OffsetDateTime updateTime = actualBlob.getUpdateTimeOffsetDateTime();
                        Instant updated = updateTime != null ? updateTime.toInstant() : null;
                        return new ReceiptFile(actualBlob.getName(), actualBlob.getSize(), updated, 
                            actualBlob.getContentType(), blobOwner);
                    }
                }
            }
            return null;
        } catch (StorageException ex) {
            throw new ReceiptStorageException("Unable to check for duplicate receipts", ex);
        }
    }
    
    private void createHashIndexEntry(String contentHash, String objectName, ReceiptOwner owner) {
        // SHA-256 produces 64-character hex string; validate proper length and format
        if (!isValidSha256Hash(contentHash)) {
            throw new ReceiptStorageException("Cannot create hash index for " + objectName + ": invalid hash format");
        }
        
        try {
            // Create a small index blob under hash-prefixed path for fast lookup
            String hashPrefix = contentHash.substring(0, HASH_PREFIX_LENGTH);
            String indexPath = HASH_INDEX_PREFIX + hashPrefix + "/" + contentHash;
            
            Map<String, String> indexMetadata = new HashMap<>();
            indexMetadata.put(CONTENT_HASH_METADATA_KEY, contentHash);
            indexMetadata.put("receipt-object-name", objectName);
            if (owner != null && owner.hasValues()) {
                indexMetadata.putAll(owner.toMetadata());
            }
            
            BlobInfo indexBlob = BlobInfo.newBuilder(BlobId.of(properties.getBucket(), indexPath))
                .setContentType("application/json")
                .setMetadata(indexMetadata)
                .build();
            
            // Create empty index entry (just metadata)
            storage.create(indexBlob, new byte[0]);
        } catch (StorageException ex) {
            // Fail upload if index creation fails to prevent data inconsistency
            // Without the index, this receipt won't be detected as a duplicate in future uploads
            throw new ReceiptStorageException("Failed to create hash index entry for " + objectName, ex);
        }
    }
}

