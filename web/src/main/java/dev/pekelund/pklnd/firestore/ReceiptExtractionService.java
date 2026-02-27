package dev.pekelund.pklnd.firestore;

import dev.pekelund.pklnd.storage.ReceiptOwner;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ReceiptExtractionService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptSearchService receiptSearchService;
    private final ReceiptItemService receiptItemService;

    public ReceiptExtractionService(
        ReceiptRepository receiptRepository,
        ReceiptSearchService receiptSearchService,
        ReceiptItemService receiptItemService
    ) {
        this.receiptRepository = receiptRepository;
        this.receiptSearchService = receiptSearchService;
        this.receiptItemService = receiptItemService;
    }

    public boolean isEnabled() {
        return receiptRepository.isEnabled();
    }

    public List<ParsedReceipt> listReceiptsForOwner(ReceiptOwner owner) {
        return receiptRepository.listReceiptsForOwner(owner);
    }

    public List<ParsedReceipt> listAllReceipts() {
        return receiptRepository.listAllReceipts();
    }

    public Optional<ParsedReceipt> findById(String id) {
        return receiptRepository.findById(id);
    }

    public List<ParsedReceipt> findByIds(Collection<String> ids) {
        return receiptRepository.findByIds(ids);
    }

    public void prepareReceiptForReparse(ParsedReceipt receipt) {
        receiptRepository.prepareReceiptForReparse(receipt);
    }

    public void deleteReceiptsForOwner(ReceiptOwner owner) {
        receiptRepository.deleteReceiptsForOwner(owner);
    }

    public List<ParsedReceipt> searchByItemName(String searchQuery, ReceiptOwner owner, boolean includeAllOwners) {
        return receiptSearchService.searchByItemName(searchQuery, owner, includeAllOwners);
    }

    public List<SearchItemResult> searchItemsByName(String searchQuery, ReceiptOwner owner, boolean includeAllOwners) {
        return receiptSearchService.searchItemsByName(searchQuery, owner, includeAllOwners);
    }

    public Map<String, Long> loadItemOccurrences(Collection<String> normalizedEans, ReceiptOwner owner,
        boolean includeAllOwners) {
        return receiptItemService.loadItemOccurrences(normalizedEans, owner, includeAllOwners);
    }

    public List<ReceiptItemReference> findReceiptItemReferences(String normalizedEan, ReceiptOwner owner,
        boolean includeAllOwners) {
        return receiptItemService.findReceiptItemReferences(normalizedEan, owner, includeAllOwners);
    }

    public record ReceiptItemReference(
        String receiptId,
        String ownerId,
        Instant receiptUpdatedAt,
        String receiptDate,
        String receiptStoreName,
        String receiptDisplayName,
        String receiptObjectName,
        Map<String, Object> itemData
    ) {
        public ReceiptItemReference {
            itemData = itemData == null ? Map.of() : Map.copyOf(itemData);
        }
    }
}
