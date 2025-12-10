package dev.pekelund.pklnd.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
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

        // Calculate the correct hash for the test content
        ReceiptOwner owner = new ReceiptOwner("user-123", "Test User", "test@example.com");
        String contentHash = calculateExpectedHash(fileContent);
        String hashPrefix = contentHash.substring(0, 4);
        
        // Mock existing index blob with same content hash and owner
        Blob indexBlob = mock(Blob.class);
        when(indexBlob.isDirectory()).thenReturn(false);
        when(indexBlob.getName()).thenReturn(".receipt-hashes/" + hashPrefix + "/" + contentHash);
        
        // Set the content hash metadata and owner metadata
        Map<String, String> indexMetadata = new HashMap<>();
        indexMetadata.put("content-sha256", contentHash);
        indexMetadata.put("receipt-object-name", "existing-receipt.pdf");
        indexMetadata.putAll(owner.toMetadata());
        when(indexBlob.getMetadata()).thenReturn(indexMetadata);
        
        // Mock the actual receipt blob
        Blob receiptBlob = mock(Blob.class);
        when(receiptBlob.getName()).thenReturn("existing-receipt.pdf");
        when(receiptBlob.getSize()).thenReturn(100L);
        when(receiptBlob.getContentType()).thenReturn("application/pdf");
        when(receiptBlob.getUpdateTimeOffsetDateTime())
            .thenReturn(OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));

        @SuppressWarnings("unchecked")
        Page<Blob> indexPage = mock(Page.class);
        when(indexPage.iterateAll()).thenReturn(List.of(indexBlob));
        when(storage.list(eq("test-bucket"), any(Storage.BlobListOption.class))).thenReturn(indexPage);
        when(storage.get("test-bucket", "existing-receipt.pdf")).thenReturn(receiptBlob);

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

        // Mock empty index (no duplicates)
        @SuppressWarnings("unchecked")
        Page<Blob> emptyPage = mock(Page.class);
        when(emptyPage.iterateAll()).thenReturn(List.of());
        when(storage.list(eq("test-bucket"), any(Storage.BlobListOption.class))).thenReturn(emptyPage);

        // Mock successful upload
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(mock(Blob.class));

        // Upload should succeed
        ReceiptOwner owner = new ReceiptOwner("user-123", "Test User", "test@example.com");
        List<StoredReceiptReference> result = service.uploadFiles(List.of(file), owner);

        assertThat(result).hasSize(1);
        // Verify create was called twice: once for receipt, once for hash index
        verify(storage, org.mockito.Mockito.times(2)).create(any(BlobInfo.class), any(byte[].class));
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
        Page<Blob> emptyPage = mock(Page.class);
        when(emptyPage.iterateAll()).thenReturn(List.of());
        when(storage.list(eq("test-bucket"), any(Storage.BlobListOption.class))).thenReturn(emptyPage);

        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(mock(Blob.class));

        ReceiptOwner owner = new ReceiptOwner("user-123", "Test User", "test@example.com");
        service.uploadFiles(List.of(file), owner);

        ArgumentCaptor<BlobInfo> blobInfoCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        // Verify create was called twice: once for receipt, once for hash index
        verify(storage, org.mockito.Mockito.times(2)).create(blobInfoCaptor.capture(), any(byte[].class));

        // Get the first captured BlobInfo (the receipt, not the index)
        List<BlobInfo> capturedBlobs = blobInfoCaptor.getAllValues();
        BlobInfo receiptBlobInfo = capturedBlobs.get(0);
        assertThat(receiptBlobInfo.getMetadata())
            .containsKey("content-sha256")
            .containsEntry("content-sha256", service.calculateSha256Hash(fileContent));
    }

    @Test
    void allowsDifferentOwnersToUploadSameReceipt() throws IOException {
        // Create a file with specific content
        byte[] fileContent = "Same receipt content".getBytes();
        String contentHash = calculateExpectedHash(fileContent);
        String hashPrefix = contentHash.substring(0, 4);
        
        // First owner uploads the receipt
        ReceiptOwner owner1 = new ReceiptOwner("user-1", "User One", "user1@example.com");
        
        // Mock existing index blob for owner1
        Blob indexBlob1 = mock(Blob.class);
        when(indexBlob1.isDirectory()).thenReturn(false);
        when(indexBlob1.getName()).thenReturn(".receipt-hashes/" + hashPrefix + "/" + contentHash);
        Map<String, String> metadata1 = new HashMap<>();
        metadata1.put("content-sha256", contentHash);
        metadata1.put("receipt-object-name", "owner1-receipt.pdf");
        metadata1.putAll(owner1.toMetadata());
        when(indexBlob1.getMetadata()).thenReturn(metadata1);
        
        @SuppressWarnings("unchecked")
        Page<Blob> indexPage = mock(Page.class);
        when(indexPage.iterateAll()).thenReturn(List.of(indexBlob1));
        when(storage.list(eq("test-bucket"), any(Storage.BlobListOption.class))).thenReturn(indexPage);
        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(mock(Blob.class));
        
        // Second owner tries to upload - should succeed because different owner
        ReceiptOwner owner2 = new ReceiptOwner("user-2", "User Two", "user2@example.com");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "receipt.pdf",
            "application/pdf",
            fileContent
        );
        
        List<StoredReceiptReference> result = service.uploadFiles(List.of(file), owner2);
        
        assertThat(result).hasSize(1);
        // Verify upload was allowed for different owner
        verify(storage, org.mockito.Mockito.times(2)).create(any(BlobInfo.class), any(byte[].class));
    }

    @Test
    void deletesHashIndexWhenDeletingReceipt() throws IOException {
        ReceiptOwner owner = new ReceiptOwner("user-123", "Test User", "test@example.com");
        String contentHash = "a".repeat(64); // Valid 64-char hex hash
        
        // Mock receipt blob with hash metadata
        Blob receiptBlob = mock(Blob.class);
        when(receiptBlob.isDirectory()).thenReturn(false);
        when(receiptBlob.getName()).thenReturn("receipt-file.pdf");
        Map<String, String> receiptMetadata = new HashMap<>();
        receiptMetadata.put("content-sha256", contentHash);
        receiptMetadata.putAll(owner.toMetadata());
        when(receiptBlob.getMetadata()).thenReturn(receiptMetadata);
        when(receiptBlob.getBlobId()).thenReturn(BlobId.of("test-bucket", "receipt-file.pdf"));
        
        @SuppressWarnings("unchecked")
        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of(receiptBlob));
        when(storage.list("test-bucket")).thenReturn(page);
        
        service.deleteReceiptsForOwner(owner);
        
        // Verify receipt was deleted
        verify(storage).delete(receiptBlob.getBlobId());
        // Verify hash index was also deleted
        verify(storage).delete(BlobId.of("test-bucket", ".receipt-hashes/aaaa/" + contentHash));
    }

    @Test
    void filtersHashIndexFromReceiptList() {
        // Mock receipt blob
        Blob receiptBlob = mock(Blob.class);
        when(receiptBlob.isDirectory()).thenReturn(false);
        when(receiptBlob.getName()).thenReturn("receipt-file.pdf");
        when(receiptBlob.getSize()).thenReturn(1000L);
        when(receiptBlob.getContentType()).thenReturn("application/pdf");
        when(receiptBlob.getUpdateTimeOffsetDateTime())
            .thenReturn(OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        when(receiptBlob.getMetadata()).thenReturn(Map.of());
        
        // Mock hash index blob (should be filtered out)
        Blob hashIndexBlob = mock(Blob.class);
        when(hashIndexBlob.isDirectory()).thenReturn(false);
        when(hashIndexBlob.getName()).thenReturn(".receipt-hashes/abcd/abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
        
        @SuppressWarnings("unchecked")
        Page<Blob> page = mock(Page.class);
        when(page.iterateAll()).thenReturn(List.of(receiptBlob, hashIndexBlob));
        when(storage.list("test-bucket")).thenReturn(page);
        
        List<ReceiptFile> result = service.listReceipts();
        
        // Should only include the receipt, not the hash index
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("receipt-file.pdf");
    }

    private String calculateExpectedHash(byte[] content) {
        // Use the production method via the service instance
        return service.calculateSha256Hash(content);
    }
}
