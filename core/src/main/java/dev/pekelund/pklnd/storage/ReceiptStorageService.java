package dev.pekelund.pklnd.storage;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ReceiptStorageService {

    boolean isEnabled();

    List<ReceiptFile> listReceipts();

    List<StoredReceiptReference> uploadFiles(List<MultipartFile> files, ReceiptOwner owner);

    void deleteReceiptsForOwner(ReceiptOwner owner);
}

