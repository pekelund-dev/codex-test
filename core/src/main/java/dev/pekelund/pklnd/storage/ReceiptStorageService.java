package dev.pekelund.pklnd.storage;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ReceiptStorageService {

    boolean isEnabled();

    List<ReceiptFile> listReceipts();

    /**
     * Upload multiple receipt files, processing all files even if some are duplicates.
     * @return result containing successfully uploaded receipts and any failures
     */
    UploadResult uploadFilesWithResults(List<MultipartFile> files, ReceiptOwner owner);

    /**
     * @deprecated Use uploadFilesWithResults for better error handling in batch uploads
     */
    @Deprecated
    List<StoredReceiptReference> uploadFiles(List<MultipartFile> files, ReceiptOwner owner);

    void deleteReceiptsForOwner(ReceiptOwner owner);
}

