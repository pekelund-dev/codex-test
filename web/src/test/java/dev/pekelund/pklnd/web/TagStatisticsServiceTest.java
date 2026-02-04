package dev.pekelund.pklnd.web;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import dev.pekelund.pklnd.firestore.ItemCategorizationService;
import dev.pekelund.pklnd.firestore.FirestoreProperties;
import dev.pekelund.pklnd.firestore.ItemTag;
import dev.pekelund.pklnd.firestore.ParsedReceipt;
import dev.pekelund.pklnd.firestore.ReceiptExtractionService;
import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class TagStatisticsServiceTest {

    @Test
    void summarizeTags_ShouldAggregateCountsAndTotals() {
        ItemCategorizationService categorizationService = mock(ItemCategorizationService.class);
        ReceiptExtractionService receiptExtractionService = mock(ReceiptExtractionService.class);

        when(categorizationService.isEnabled()).thenReturn(true);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        ReceiptOwner owner = new ReceiptOwner("user-1", "User", "user@example.com");
        ParsedReceipt receipt = new ParsedReceipt(
            "receipt-1",
            null,
            null,
            null,
            owner,
            null,
            null,
            Instant.now(),
            Map.of("storeName", "ICA"),
            List.of(Map.of("totalPrice", new BigDecimal("10.50"), "normalizedEan", "12345678")),
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null
        );

        when(receiptExtractionService.findById("receipt-1")).thenReturn(Optional.of(receipt));
        when(categorizationService.getItemsByTag("tag-1", "user-1")).thenReturn(List.of(
            new ItemCategorizationService.TaggedItemInfo("receipt-1", "0", null, Instant.now())
        ));

        FirestoreProperties properties = new FirestoreProperties();
        @SuppressWarnings("unchecked")
        ObjectProvider<com.google.cloud.firestore.Firestore> firestoreProvider = mock(ObjectProvider.class);
        when(firestoreProvider.getIfAvailable()).thenReturn(null);
        ReceiptOwnerResolver receiptOwnerResolver = mock(ReceiptOwnerResolver.class);
        Authentication authentication = mock(Authentication.class);
        when(receiptOwnerResolver.resolve(authentication)).thenReturn(owner);

        TagStatisticsService service = new TagStatisticsService(
            properties,
            firestoreProvider,
            receiptOwnerResolver,
            java.util.Optional.of(categorizationService),
            java.util.Optional.of(receiptExtractionService)
        );

        ItemTag tag = ItemTag.builder().id("tag-1").name("Frys").build();
        Map<String, TagStatisticsService.TagSummary> summaries = service.summarizeTags(List.of(tag), authentication);

        TagStatisticsService.TagSummary summary = summaries.get("tag-1");
        assertThat(summary).isNotNull();
        assertThat(summary.itemCount()).isEqualTo(1);
        assertThat(summary.storeCount()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualByComparingTo(new BigDecimal("10.50"));
    }

    @Test
    void summarizeTags_ShouldUseCachedSummaryWhenFresh() throws Exception {
        ItemCategorizationService categorizationService = mock(ItemCategorizationService.class);
        when(categorizationService.isEnabled()).thenReturn(true);
        ReceiptExtractionService receiptExtractionService = mock(ReceiptExtractionService.class);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        FirestoreProperties properties = new FirestoreProperties();
        properties.setEnabled(true);

        Authentication authentication = mock(Authentication.class);
        ReceiptOwnerResolver receiptOwnerResolver = mock(ReceiptOwnerResolver.class);
        when(receiptOwnerResolver.resolve(authentication)).thenReturn(new ReceiptOwner("user-1", null, null));

        Firestore firestore = mock(Firestore.class);
        CollectionReference summariesCollection = mock(CollectionReference.class);
        DocumentReference summaryDocument = mock(DocumentReference.class);
        DocumentSnapshot summarySnapshot = mock(DocumentSnapshot.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> summaryFuture = mock(ApiFuture.class);

        when(summarySnapshot.exists()).thenReturn(true);
        when(summarySnapshot.getId()).thenReturn("user-1:tag-1");
        when(summarySnapshot.get("computedAt")).thenReturn(Timestamp.now());
        when(summarySnapshot.get("itemCount")).thenReturn(3);
        when(summarySnapshot.get("storeCount")).thenReturn(2);
        when(summarySnapshot.get("totalAmount")).thenReturn("25.00");
        when(summaryFuture.get()).thenReturn(summarySnapshot);

        when(firestore.collection(properties.getTagSummariesCollection())).thenReturn(summariesCollection);
        when(summariesCollection.document("user-1:tag-1")).thenReturn(summaryDocument);
        when(summaryDocument.get()).thenReturn(summaryFuture);

        CollectionReference metaCollection = mock(CollectionReference.class);
        DocumentReference metaDocument = mock(DocumentReference.class);
        DocumentSnapshot metaSnapshot = mock(DocumentSnapshot.class);
        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> metaFuture = mock(ApiFuture.class);
        when(metaSnapshot.exists()).thenReturn(false);
        when(metaSnapshot.getId()).thenReturn("user-1:tag-1");
        when(metaFuture.get()).thenReturn(metaSnapshot);
        when(firestore.collection(properties.getTagSummaryMetaCollection())).thenReturn(metaCollection);
        when(metaCollection.document("user-1:tag-1")).thenReturn(metaDocument);
        when(metaDocument.get()).thenReturn(metaFuture);

        @SuppressWarnings("unchecked")
        ApiFuture<List<DocumentSnapshot>> metaBatchFuture = mock(ApiFuture.class);
        @SuppressWarnings("unchecked")
        ApiFuture<List<DocumentSnapshot>> summaryBatchFuture = mock(ApiFuture.class);
        when(metaBatchFuture.get()).thenReturn(List.of(metaSnapshot));
        when(summaryBatchFuture.get()).thenReturn(List.of(summarySnapshot));
        when(firestore.getAll(any(DocumentReference[].class))).thenReturn(metaBatchFuture, summaryBatchFuture);

        @SuppressWarnings("unchecked")
        ObjectProvider<Firestore> firestoreProvider = mock(ObjectProvider.class);
        when(firestoreProvider.getIfAvailable()).thenReturn(firestore);

        TagStatisticsService service = new TagStatisticsService(
            properties,
            firestoreProvider,
            receiptOwnerResolver,
            Optional.of(categorizationService),
            Optional.of(receiptExtractionService)
        );

        ItemTag tag = ItemTag.builder().id("tag-1").name("Frys").build();
        Map<String, TagStatisticsService.TagSummary> summaries = service.summarizeTags(List.of(tag), authentication);

        TagStatisticsService.TagSummary summary = summaries.get("tag-1");
        assertThat(summary).isNotNull();
        assertThat(summary.itemCount()).isEqualTo(3);
        assertThat(summary.storeCount()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
        verify(categorizationService, never()).getItemsByTag(any(), any());
    }

    @Test
    void summarizeTags_ShouldBatchFirestoreReads() throws Exception {
        ItemCategorizationService categorizationService = mock(ItemCategorizationService.class);
        when(categorizationService.isEnabled()).thenReturn(true);
        when(categorizationService.getItemsByTag(any(), any())).thenReturn(List.of());

        ReceiptExtractionService receiptExtractionService = mock(ReceiptExtractionService.class);
        when(receiptExtractionService.isEnabled()).thenReturn(true);

        FirestoreProperties properties = new FirestoreProperties();
        properties.setEnabled(true);

        Authentication authentication = mock(Authentication.class);
        ReceiptOwnerResolver receiptOwnerResolver = mock(ReceiptOwnerResolver.class);
        when(receiptOwnerResolver.resolve(authentication)).thenReturn(new ReceiptOwner("user-1", null, null));

        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference document = mock(DocumentReference.class);
        when(firestore.collection(anyString())).thenReturn(collection);
        when(collection.document(anyString())).thenReturn(document);

        @SuppressWarnings("unchecked")
        ApiFuture<List<DocumentSnapshot>> batchFuture = mock(ApiFuture.class);
        when(batchFuture.get()).thenReturn(List.of());
        when(firestore.getAll(any(DocumentReference[].class))).thenReturn(batchFuture);

        @SuppressWarnings("unchecked")
        ObjectProvider<Firestore> firestoreProvider = mock(ObjectProvider.class);
        when(firestoreProvider.getIfAvailable()).thenReturn(firestore);

        TagStatisticsService service = new TagStatisticsService(
            properties,
            firestoreProvider,
            receiptOwnerResolver,
            Optional.of(categorizationService),
            Optional.of(receiptExtractionService)
        );

        List<ItemTag> tags = new ArrayList<>();
        for (int i = 0; i < 301; i++) {
            tags.add(ItemTag.builder().id("tag-" + i).name("Tagg " + i).build());
        }

        service.summarizeTags(tags, authentication);

        verify(firestore, times(4)).getAll(any(DocumentReference[].class));
    }
}
