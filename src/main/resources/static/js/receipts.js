(function () {
    const dropzone = document.getElementById('receipt-dropzone');
    const fileInput = document.getElementById('receipt-files');
    const triggerButton = document.getElementById('trigger-file-select');
    const selectedList = document.getElementById('selected-files');
    const uploadButton = document.getElementById('upload-button');
    const hint = document.getElementById('file-limit-hint');
    const form = dropzone?.closest('form');

    if (!dropzone || !fileInput || !selectedList || !uploadButton) {
        return;
    }

    const MAX_FILES = 10;
    const DEFAULT_HINT = `You can add up to ${MAX_FILES} files per upload.`;
    const FALLBACK_UPLOAD_BUTTON_TEXT = 'Upload receipt files';
    const originalButtonText = uploadButton.textContent;

    let selectedFiles = [];

    function formatBytes(bytes) {
        if (!bytes || bytes <= 0) {
            return '0 B';
        }
        const units = ['B', 'KB', 'MB', 'GB'];
        let size = bytes;
        let unit = 0;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit += 1;
        }
        return `${size.toFixed(1)} ${units[unit]}`;
    }

    function buildTransfer() {
        if (typeof DataTransfer !== 'undefined') {
            try {
                return new DataTransfer();
            } catch (error) {
                // Some browsers (for example Safari < 16.4) expose DataTransfer but do not allow constructing it.
            }
        }

        // No safe or semantically correct fallback for DataTransfer exists.
        // ClipboardEvent is not used as a fallback because it is semantically different
        // and may lead to confusing or unreliable behavior.
        return null;
    }

    const supportsFileAssignment = buildTransfer() !== null;

    function syncFileInput() {
        if (!supportsFileAssignment) {
            return false;
        }

        const transfer = buildTransfer();
        if (!transfer) {
            return false;
        }

        selectedFiles.forEach((file) => transfer.items.add(file));
        fileInput.files = transfer.files;
        return true;
    }

    function updateHint(message) {
        if (hint) {
            hint.textContent = message;
        }
    }

    function refreshSelectedFiles() {
        selectedList.innerHTML = '';

        if (selectedFiles.length === 0) {
            uploadButton.disabled = true;
            updateHint(DEFAULT_HINT);
            return;
        }

        selectedFiles.forEach((file, index) => {
            const item = document.createElement('li');
            item.className = 'list-group-item d-flex justify-content-between align-items-center';

            const details = document.createElement('div');
            details.className = 'text-start';
            details.innerHTML = `<strong>${file.name}</strong><br><span class="text-muted small">${formatBytes(file.size)}</span>`;

            const removeButton = document.createElement('button');
            removeButton.type = 'button';
            removeButton.className = 'btn btn-link text-danger p-0 small';
            removeButton.textContent = 'Remove';
            removeButton.addEventListener('click', () => {
                selectedFiles = selectedFiles.filter((_, itemIndex) => itemIndex !== index);
                syncFileInput();
                refreshSelectedFiles();
            });

            item.append(details, removeButton);
            selectedList.appendChild(item);
        });

        uploadButton.disabled = false;
        updateHint(`${selectedFiles.length} file${selectedFiles.length === 1 ? '' : 's'} ready for upload.`);
    }

    function handleFiles(newFiles) {
        if (!newFiles || newFiles.length === 0) {
            return;
        }

        const incomingFiles = Array.from(newFiles).filter((file) => file && file.size >= 0);
        if (incomingFiles.length === 0) {
            return;
        }

        let combined = selectedFiles.concat(incomingFiles);
        if (combined.length > MAX_FILES) {
            combined = combined.slice(0, MAX_FILES);
            updateHint(`Only the first ${MAX_FILES} files were added.`);
        }

        selectedFiles = combined;
        syncFileInput();
        refreshSelectedFiles();
    }

    triggerButton?.addEventListener('click', () => fileInput.click());

    fileInput.addEventListener('change', (event) => {
        handleFiles(event.target.files);
        event.target.value = '';
    });

    ['dragenter', 'dragover'].forEach((type) => {
        dropzone.addEventListener(type, (event) => {
            event.preventDefault();
            dropzone.classList.add('is-dragover');
        });
    });

    ['dragleave', 'drop'].forEach((type) => {
        dropzone.addEventListener(type, (event) => {
            event.preventDefault();
            dropzone.classList.remove('is-dragover');
        });
    });

    dropzone.addEventListener('drop', (event) => {
        if (event.dataTransfer?.files) {
            handleFiles(event.dataTransfer.files);
        }
    });

    if (form && !supportsFileAssignment) {
        form.addEventListener('submit', (event) => {
            if (selectedFiles.length === 0) {
                updateHint('Please choose at least one file to upload.');
                event.preventDefault();
                return;
            }

            event.preventDefault();

            const formData = new FormData(form);
            formData.delete('files');
            selectedFiles.forEach((file) => formData.append('files', file));

            uploadButton.disabled = true;
            uploadButton.textContent = 'Uploading…';

            fetch(form.action, {
                method: form.method || 'POST',
                body: formData,
                credentials: 'same-origin',
            }).then((response) => {
                if (!response.ok) {
                    throw new Error('Upload failed');
                }
                if (response.redirected) {
                    window.location.href = response.url;
                } else {
                    const locationHeader = response.headers.get('Location');
                    if (locationHeader) {
                        window.location.href = locationHeader;
                    } else {
                        // No redirect destination provided; restore the button so the user can continue.
                        uploadButton.disabled = false;
                        uploadButton.textContent = originalButtonText || FALLBACK_UPLOAD_BUTTON_TEXT;
                    }
                }
            }).catch(() => {
                uploadButton.disabled = false;
                uploadButton.textContent = originalButtonText || FALLBACK_UPLOAD_BUTTON_TEXT;
                updateHint('Upload failed. Please try again.');
            });
        });
    }

    refreshSelectedFiles();
})();

