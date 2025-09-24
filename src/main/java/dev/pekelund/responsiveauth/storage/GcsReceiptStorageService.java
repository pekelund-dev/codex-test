package dev.pekelund.responsiveauth.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnBean(Storage.class)
public class GcsReceiptStorageService implements ReceiptStorageService {

    private static final Pattern UNSAFE_FILENAME = Pattern.compile("[^A-Za-z0-9._-]");
    private static final DateTimeFormatter OBJECT_PREFIX =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.US).withZone(ZoneOffset.UTC);

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
                files.add(new ReceiptFile(blob.getName(), blob.getSize(),
                    blob.getUpdateTime() != null ? Instant.ofEpochMilli(blob.getUpdateTime()) : null,
                    blob.getContentType()));
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
    public void uploadFiles(List<MultipartFile> files) {
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String originalFilename = file.getOriginalFilename();
            String objectName = buildObjectName(originalFilename);
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.getBucket(), objectName))
                .setContentType(file.getContentType())
                .build();

            try (InputStream inputStream = file.getInputStream()) {
                storage.createFrom(blobInfo, inputStream);
            } catch (IOException | StorageException ex) {
                String displayName = StringUtils.hasText(originalFilename) ? originalFilename : objectName;
                throw new ReceiptStorageException("Failed to upload file '%s'".formatted(displayName), ex);
            }
        }
    }

    private String buildObjectName(String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename : "receipt";
        filename = filename.replace('\\', '/');
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < filename.length() - 1) {
            filename = filename.substring(slashIndex + 1);
        }
        filename = UNSAFE_FILENAME.matcher(filename).replaceAll("_");
        if (!StringUtils.hasText(filename)) {
            filename = "receipt";
        }
        String prefix = OBJECT_PREFIX.format(Instant.now());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "_" + suffix + "_" + filename;
    }
}

