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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
                files.add(new ReceiptFile(blob.getName(), blob.getSize(),
                    blob.getUpdateTime() != null ? Instant.ofEpochMilli(blob.getUpdateTime()) : null,
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
            String objectName = buildObjectName(originalFilename);
            BlobInfo.Builder blobInfoBuilder = BlobInfo.newBuilder(BlobId.of(properties.getBucket(), objectName))
                .setContentType(file.getContentType());
            Map<String, String> metadata = owner != null && owner.hasValues() ? owner.toMetadata() : Map.of();
            if (!metadata.isEmpty()) {
                blobInfoBuilder.setMetadata(metadata);
            }
            BlobInfo blobInfo = blobInfoBuilder.build();

            try (InputStream inputStream = file.getInputStream()) {
                storage.createFrom(blobInfo, inputStream);
                uploaded.add(new StoredReceiptReference(properties.getBucket(), objectName, owner));
            } catch (IOException | StorageException ex) {
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
}

