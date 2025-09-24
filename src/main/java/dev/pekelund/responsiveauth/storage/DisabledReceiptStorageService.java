package dev.pekelund.responsiveauth.storage;

import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnMissingBean(ReceiptStorageService.class)
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
    public void uploadFiles(List<MultipartFile> files) {
        throw new ReceiptStorageException("Google Cloud Storage integration is disabled");
    }
}

