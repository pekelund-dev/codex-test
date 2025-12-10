package dev.pekelund.pklnd.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class GcsReceiptStorageServiceTest {

    private Storage storage;
    private GcsProperties properties;
    private GcsReceiptStorageService service;

    @BeforeEach
    void setUp() {
        storage = mock(Storage.class);
        properties = new GcsProperties();
        properties.setBucket("test-bucket");
        service = new GcsReceiptStorageService(storage, properties);
    }

    @Test
    void rejectsDuplicateReceiptUpload() throws IOException {
        // Create a file with specific content
        byte[] fileContent = "Test receipt content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "receipt.pdf",
            "application/pdf",
            fileContent
        );

        // Mock existing blob with same content hash and owner
        Blob existingBlob = mock(Blob.class);
        when(existingBlob.isDirectory()).thenReturn(false);
        when(existingBlob.getName()).thenReturn("existing-receipt.pdf");
        when(existingBlob.getSize()).thenReturn(100L);
        when(existingBlob.getContentType()).thenReturn("application/pdf");
        when(existingBlob.getUpdateTimeOffsetDateTime())
            .thenReturn(OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        
        // Set the content hash metadata and owner metadata
        ReceiptOwner owner = new ReceiptOwner("user-123", "Test User", "test@example.com");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("content-sha256", calculateExpectedHash(fileContent));
        metadata.putAll(owner.toMetadata());
        when(existingBlob.getMetadata()).thenReturn(metadata);

        @SuppressWarnings("unchecked")
        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of(existingBlob));
        when(storage.list("test-bucket")).thenReturn(page);

        // Attempt to upload - should throw DuplicateReceiptException
        assertThatThrownBy(() -> service.uploadFiles(List.of(file), owner))
            .isInstanceOf(DuplicateReceiptException.class)
            .hasMessageContaining("receipt.pdf")
            .hasMessageContaining("har redan laddats upp");

        // Verify that we didn't attempt to upload
        verify(storage, never()).create(any(BlobInfo.class), any(byte[].class));
    }

    @Test
    void allowsUploadWhenNoMatchingHashExists() throws IOException {
        // Create a file
        byte[] fileContent = "New unique receipt content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "new-receipt.pdf",
            "application/pdf",
            fileContent
        );

        // Mock existing blob with different content hash
        Blob existingBlob = mock(Blob.class);
        when(existingBlob.isDirectory()).thenReturn(false);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("content-sha256", "different-hash-value");
        when(existingBlob.getMetadata()).thenReturn(metadata);

        @SuppressWarnings("unchecked")
        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of(existingBlob));
        when(storage.list("test-bucket")).thenReturn(page);

        // Mock successful upload
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(mock(Blob.class));

        // Upload should succeed
        ReceiptOwner owner = new ReceiptOwner("user-123", "Test User", "test@example.com");
        List<StoredReceiptReference> result = service.uploadFiles(List.of(file), owner);

        assertThat(result).hasSize(1);
        verify(storage).create(any(BlobInfo.class), any(byte[].class));
    }

    @Test
    void storesContentHashInMetadata() throws IOException {
        byte[] fileContent = "Receipt content for hash test".getBytes();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "receipt-hash.pdf",
            "application/pdf",
            fileContent
        );

        @SuppressWarnings("unchecked")
        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of());
        when(storage.list("test-bucket")).thenReturn(page);

        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(mock(Blob.class));

        ReceiptOwner owner = new ReceiptOwner("user-123", "Test User", "test@example.com");
        service.uploadFiles(List.of(file), owner);

        ArgumentCaptor<BlobInfo> blobInfoCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).create(blobInfoCaptor.capture(), any(byte[].class));

        BlobInfo capturedBlobInfo = blobInfoCaptor.getValue();
        assertThat(capturedBlobInfo.getMetadata())
            .containsKey("content-sha256")
            .containsEntry("content-sha256", service.calculateSha256Hash(fileContent));
    }

    private String calculateExpectedHash(byte[] content) {
        // Use the production method via the service instance
        return service.calculateSha256Hash(content);
    }
}
