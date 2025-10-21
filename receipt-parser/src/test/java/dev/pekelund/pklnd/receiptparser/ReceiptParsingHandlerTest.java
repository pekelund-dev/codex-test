package dev.pekelund.pklnd.receiptparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Blob.Builder;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReceiptParsingHandlerTest {

    private Storage storage;
    private ReceiptExtractionRepository repository;
    private ReceiptDataExtractor extractor;
    private ReceiptParsingHandler handler;

    @BeforeEach
    void setUp() {
        storage = mock(Storage.class);
        repository = mock(ReceiptExtractionRepository.class);
        extractor = mock(ReceiptDataExtractor.class);
        handler = new ReceiptParsingHandler(storage, repository, extractor);
    }

    @Test
    void mergesEventMetadataWhenBlobMissingOwner() {
        StorageObjectEvent event = new StorageObjectEvent();
        event.setBucket("bucket");
        event.setName("receipts/sample.pdf");
        event.setMetadata(Map.of(
            ReceiptOwner.METADATA_OWNER_ID, "user-123",
            ReceiptOwner.METADATA_OWNER_EMAIL, "user@example.com"
        ));

        Blob blob = mock(Blob.class);
        Builder builder = mock(Builder.class);
        Blob updatedBlob = mock(Blob.class);

        when(storage.get(eq(BlobId.of("bucket", "receipts/sample.pdf")))).thenReturn(blob);
        when(blob.getMetadata()).thenReturn(Map.of());
        when(blob.getContentType()).thenReturn("application/pdf");
        when(blob.getName()).thenReturn("receipts/sample.pdf");
        when(blob.getContent()).thenReturn(new byte[0]);
        when(blob.toBuilder()).thenReturn(builder);

        when(builder.setMetadata(anyMap())).thenReturn(builder);
        when(builder.build()).thenReturn(updatedBlob);
        when(updatedBlob.update()).thenReturn(updatedBlob);

        when(updatedBlob.toBuilder()).thenReturn(builder);
        when(updatedBlob.getContentType()).thenReturn("application/pdf");
        when(updatedBlob.getName()).thenReturn("receipts/sample.pdf");
        when(updatedBlob.getContent()).thenReturn(new byte[0]);

        when(extractor.extract(any(), any())).thenReturn(new ReceiptExtractionResult(Map.of(), "{}"));

        handler.handle(event);

        ArgumentCaptor<ReceiptOwner> ownerCaptor = ArgumentCaptor.forClass(ReceiptOwner.class);
        verify(repository).saveExtraction(eq("bucket"), eq("receipts/sample.pdf"), ownerCaptor.capture(), any(), any());
        ReceiptOwner capturedOwner = ownerCaptor.getValue();
        assertThat(capturedOwner).isNotNull();
        assertThat(capturedOwner.id()).isEqualTo("user-123");
        assertThat(capturedOwner.email()).isEqualTo("user@example.com");

        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(builder, atLeastOnce()).setMetadata(metadataCaptor.capture());
        Map<String, String> finalMetadata = metadataCaptor.getValue();
        assertThat(finalMetadata)
            .containsEntry(ReceiptOwner.METADATA_OWNER_ID, "user-123")
            .containsEntry(ReceiptOwner.METADATA_OWNER_EMAIL, "user@example.com");
    }

    @Test
    void usesEventOwnerWhenBlobMetadataMissing() {
        StorageObjectEvent event = new StorageObjectEvent();
        event.setBucket("bucket");
        event.setName("receipts/missing.pdf");
        event.setOwner(Map.of(
            "id", "user-456",
            "displayName", "Olle Olsson",
            "email", "olle@example.com"
        ));

        Blob blob = mock(Blob.class);
        Builder builder = mock(Builder.class);
        Blob updatedBlob = mock(Blob.class);

        when(storage.get(eq(BlobId.of("bucket", "receipts/missing.pdf")))).thenReturn(blob);
        when(blob.getMetadata()).thenReturn(null);
        when(blob.getContentType()).thenReturn("application/pdf");
        when(blob.getName()).thenReturn("receipts/missing.pdf");
        when(blob.getContent()).thenReturn(new byte[0]);
        when(blob.toBuilder()).thenReturn(builder);

        when(builder.setMetadata(anyMap())).thenReturn(builder);
        when(builder.build()).thenReturn(updatedBlob);
        when(updatedBlob.update()).thenReturn(updatedBlob);

        when(updatedBlob.toBuilder()).thenReturn(builder);
        when(updatedBlob.getContentType()).thenReturn("application/pdf");
        when(updatedBlob.getName()).thenReturn("receipts/missing.pdf");
        when(updatedBlob.getContent()).thenReturn(new byte[0]);

        when(extractor.extract(any(), any())).thenReturn(new ReceiptExtractionResult(Map.of(), "{}"));

        handler.handle(event);

        ArgumentCaptor<ReceiptOwner> ownerCaptor = ArgumentCaptor.forClass(ReceiptOwner.class);
        verify(repository).saveExtraction(eq("bucket"), eq("receipts/missing.pdf"), ownerCaptor.capture(), any(), any());
        ReceiptOwner capturedOwner = ownerCaptor.getValue();
        assertThat(capturedOwner).isNotNull();
        assertThat(capturedOwner.id()).isEqualTo("user-456");
        assertThat(capturedOwner.displayName()).isEqualTo("Olle Olsson");
        assertThat(capturedOwner.email()).isEqualTo("olle@example.com");

        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(builder, atLeastOnce()).setMetadata(metadataCaptor.capture());
        Map<String, String> finalMetadata = metadataCaptor.getValue();
        assertThat(finalMetadata)
            .containsEntry(ReceiptOwner.METADATA_OWNER_ID, "user-456")
            .containsEntry(ReceiptOwner.METADATA_OWNER_DISPLAY_NAME, "Olle Olsson")
            .containsEntry(ReceiptOwner.METADATA_OWNER_EMAIL, "olle@example.com");
    }
}
