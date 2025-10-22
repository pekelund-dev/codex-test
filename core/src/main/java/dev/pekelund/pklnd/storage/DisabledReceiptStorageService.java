package dev.pekelund.pklnd.storage;

import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnMissingBean(ReceiptStorageService.class)
@ConditionalOnProperty(value = "gcs.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledReceiptStorageService implements ReceiptStorageService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public List<ReceiptFile> listReceipts() {
        return Collections.emptyList();
    }

    @Override
    public List<StoredReceiptReference> uploadFiles(List<MultipartFile> files, ReceiptOwner owner) {
        throw new ReceiptStorageException("Google Cloud Storage integration is disabled");
    }

    @Override
    public void deleteReceiptsForOwner(ReceiptOwner owner) {
        throw new ReceiptStorageException("Google Cloud Storage integration is disabled");
    }
}

