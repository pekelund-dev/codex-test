package dev.pekelund.responsiveauth.storage;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ReceiptStorageService {

    boolean isEnabled();

    List<ReceiptFile> listReceipts();

    void uploadFiles(List<MultipartFile> files);
}

