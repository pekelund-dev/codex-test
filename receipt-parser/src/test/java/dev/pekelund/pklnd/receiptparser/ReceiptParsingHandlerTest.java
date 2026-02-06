package dev.pekelund.pklnd.receiptparser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptParsingHandlerTest {

    @Mock
    private Storage storage;

    @Mock
    private ReceiptExtractionRepository repository;

    @Mock
    private ReceiptDataExtractor extractor;

    @InjectMocks
    private ReceiptParsingHandler handler;

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
        verify(repository).saveExtraction(eq("bucket"), eq("receipts/sample.pdf"), ownerCaptor.capture(), any(), any(), anyBoolean());
        ReceiptOwner capturedOwner = ownerCaptor.getValue();
        assertThat(capturedOwner).isNotNull();
        assertThat(capturedOwner.id()).isEqualTo("user-123");
        assertThat(capturedOwner.email()).isEqualTo("user@example.com");

        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(stringStringMapClass());
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
        verify(repository).saveExtraction(eq("bucket"), eq("receipts/missing.pdf"), ownerCaptor.capture(), any(), any(), anyBoolean());
        ReceiptOwner capturedOwner = ownerCaptor.getValue();
        assertThat(capturedOwner).isNotNull();
        assertThat(capturedOwner.id()).isEqualTo("user-456");
        assertThat(capturedOwner.displayName()).isEqualTo("Olle Olsson");
        assertThat(capturedOwner.email()).isEqualTo("olle@example.com");

        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(stringStringMapClass());
        verify(builder, atLeastOnce()).setMetadata(metadataCaptor.capture());
        Map<String, String> finalMetadata = metadataCaptor.getValue();
        assertThat(finalMetadata)
            .containsEntry(ReceiptOwner.METADATA_OWNER_ID, "user-456")
            .containsEntry(ReceiptOwner.METADATA_OWNER_DISPLAY_NAME, "Olle Olsson")
            .containsEntry(ReceiptOwner.METADATA_OWNER_EMAIL, "olle@example.com");
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, String>> stringStringMapClass() {
        return (Class<Map<String, String>>) (Class<?>) Map.class;
    }
}
