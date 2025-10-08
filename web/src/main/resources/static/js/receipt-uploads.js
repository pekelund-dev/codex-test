(function () {
    const form = document.querySelector('.receipt-upload');
    if (!form) {
        return;
    }

    const dropzone = document.getElementById('receipt-dropzone');
    const fileInput = document.getElementById('receipt-files');
    const triggerButton = document.getElementById('trigger-file-select');
    const selectedList = document.getElementById('selected-files');
    const uploadButton = document.getElementById('upload-button');
    const hint = document.getElementById('file-limit-hint');
    const progressContainer = document.getElementById('upload-progress');
    const progressBar = progressContainer ? progressContainer.querySelector('.progress-bar') : null;
    const feedback = document.getElementById('upload-feedback');

    const maxFiles = Number.parseInt(form.getAttribute('data-max-files'), 10) || 50;
    const DEFAULT_HINT = `You can add up to ${maxFiles} files per upload.`;
    const FALLBACK_UPLOAD_BUTTON_TEXT = 'Upload receipt files';
    const originalButtonText = uploadButton ? uploadButton.textContent : FALLBACK_UPLOAD_BUTTON_TEXT;

    let selectedFiles = [];
    let activeRequest = null;
    let progressResetTimeoutId = null;

    function formatBytes(bytes) {
        if (!Number.isFinite(bytes) || bytes <= 0) {
            return '0 B';
        }
        const units = ['B', 'KB', 'MB', 'GB'];
        let size = bytes;
        let unitIndex = 0;
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex += 1;
        }
        return `${size.toFixed(1)} ${units[unitIndex]}`;
    }

    function truncateName(name, maxLength) {
        if (typeof name !== 'string' || name.trim() === '') {
            return '—';
        }
        const trimmed = name.trim();
        if (trimmed.length <= maxLength) {
            return trimmed;
        }
        const extensionIndex = trimmed.lastIndexOf('.');
        if (extensionIndex > 0 && extensionIndex < trimmed.length - 1) {
            const base = trimmed.substring(0, extensionIndex);
            const extension = trimmed.substring(extensionIndex);
            const allowedBase = Math.max(1, maxLength - extension.length - 1);
            return `${base.substring(0, allowedBase)}…${extension}`;
        }
        return `${trimmed.substring(0, Math.max(1, maxLength - 1))}…`;
    }

    function decodeFileName(value) {
        if (typeof value !== 'string') {
            return value;
        }
        try {
            return decodeURIComponent(value);
        } catch (error) {
            return value;
        }
    }

    function buildTransfer() {
        if (typeof DataTransfer !== 'undefined') {
            try {
                return new DataTransfer();
            } catch (error) {
                return null;
            }
        }
        return null;
    }

    const supportsFileAssignment = buildTransfer() !== null;

    function syncFileInput() {
        if (!supportsFileAssignment || !fileInput) {
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

    function toggleProgress(visible) {
        if (!progressContainer) {
            return;
        }
        if (visible && progressResetTimeoutId !== null) {
            window.clearTimeout(progressResetTimeoutId);
            progressResetTimeoutId = null;
        }
        progressContainer.classList.toggle('d-none', !visible);
        progressContainer.setAttribute('aria-hidden', visible ? 'false' : 'true');
        if (progressBar) {
            progressBar.style.width = '0%';
            progressBar.setAttribute('aria-valuenow', '0');
            progressBar.textContent = '0%';
        }
    }

    function updateProgress(loaded, total) {
        if (!progressBar) {
            return;
        }
        const safeTotal = total > 0 ? total : selectedFiles.reduce((acc, file) => acc + (file.size || 0), 0);
        const percent = safeTotal > 0 ? Math.min(100, Math.round((loaded / safeTotal) * 100)) : 0;
        progressBar.style.width = `${percent}%`;
        progressBar.setAttribute('aria-valuenow', String(percent));
        progressBar.textContent = `${percent}%`;
    }

    function showFeedback(message, type) {
        if (!feedback) {
            return;
        }
        feedback.classList.remove('d-none', 'alert-success', 'alert-danger', 'alert-info');
        feedback.classList.add(`alert-${type}`);
        feedback.textContent = message;
    }

    function clearFeedback() {
        if (!feedback) {
            return;
        }
        feedback.classList.add('d-none');
        feedback.textContent = '';
        feedback.classList.remove('alert-success', 'alert-danger', 'alert-info');
    }

    function refreshSelectedFiles() {
        if (!selectedList) {
            return;
        }
        selectedList.innerHTML = '';

        if (selectedFiles.length === 0) {
            if (uploadButton) {
                uploadButton.disabled = true;
            }
            updateHint(DEFAULT_HINT);
            return;
        }

        selectedFiles.forEach((file, index) => {
            const item = document.createElement('li');
            item.className = 'list-group-item d-flex justify-content-between align-items-center';

            const details = document.createElement('div');
            details.className = 'text-start';
            const rawName = decodeFileName(file.name);
            details.innerHTML = `<strong>${truncateName(rawName, 48)}</strong><br><span class="text-muted small">${formatBytes(file.size)}</span>`;

            const removeButton = document.createElement('button');
            removeButton.type = 'button';
            removeButton.className = 'btn btn-link text-danger p-0 small';
            removeButton.textContent = 'Remove';
            removeButton.addEventListener('click', () => {
                if (activeRequest) {
                    return;
                }
                selectedFiles = selectedFiles.filter((_, itemIndex) => itemIndex !== index);
                syncFileInput();
                refreshSelectedFiles();
            });

            item.append(details, removeButton);
            selectedList.appendChild(item);
        });

        if (uploadButton) {
            uploadButton.disabled = false;
        }
        updateHint(`${selectedFiles.length} file${selectedFiles.length === 1 ? '' : 's'} ready for upload.`);
    }

    function handleFiles(newFiles) {
        if (!newFiles || newFiles.length === 0) {
            return;
        }

        const incomingFiles = Array.from(newFiles).filter((file) => file && Number.isFinite(file.size));
        if (incomingFiles.length === 0) {
            return;
        }

        let combined = selectedFiles.concat(incomingFiles);
        if (combined.length > maxFiles) {
            combined = combined.slice(0, maxFiles);
            updateHint(`Only the first ${maxFiles} files were added.`);
        }

        selectedFiles = combined;
        syncFileInput();
        refreshSelectedFiles();
    }

    if (triggerButton && fileInput) {
        triggerButton.addEventListener('click', () => fileInput.click());
    }

    if (fileInput) {
        fileInput.addEventListener('change', (event) => {
            handleFiles(event.target.files);
            if (!supportsFileAssignment) {
                event.target.value = '';
            }
        });
    }

    if (dropzone) {
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
            if (event.dataTransfer && event.dataTransfer.files) {
                handleFiles(event.dataTransfer.files);
            }
        });
    }

    form.addEventListener('submit', (event) => {
        if (activeRequest) {
            event.preventDefault();
            return;
        }

        if (selectedFiles.length === 0) {
            updateHint('Please choose at least one file to upload.');
            event.preventDefault();
            return;
        }

        event.preventDefault();
        clearFeedback();
        toggleProgress(true);
        updateProgress(0, 0);

        if (uploadButton) {
            uploadButton.disabled = true;
            uploadButton.textContent = 'Uploading…';
        }
        if (fileInput) {
            fileInput.disabled = true;
        }
        if (triggerButton) {
            triggerButton.disabled = true;
        }

        const formData = new FormData(form);
        formData.delete('files');
        selectedFiles.forEach((file) => formData.append('files', file));

        const xhr = new XMLHttpRequest();
        activeRequest = xhr;
        const method = (form.getAttribute('method') || 'POST').toUpperCase();
        const action = form.getAttribute('action') || form.action;
        xhr.open(method, action);
        xhr.responseType = 'json';
        xhr.setRequestHeader('Accept', 'application/json');

        xhr.upload.addEventListener('progress', (progressEvent) => {
            updateProgress(progressEvent.loaded, progressEvent.total);
        });

        xhr.addEventListener('load', () => {
            activeRequest = null;
            const isSuccess = xhr.status >= 200 && xhr.status < 300;
            const payload = xhr.response;
            if (isSuccess) {
                const message = payload && payload.successMessage
                    ? payload.successMessage
                    : 'Files uploaded successfully.';
                showFeedback(message, 'success');
                selectedFiles = [];
                syncFileInput();
                refreshSelectedFiles();
                if (progressBar) {
                    progressBar.classList.remove('progress-bar-animated');
                    progressBar.classList.add('bg-success');
                    updateProgress(1, 1);
                }
            } else {
                const message = payload && payload.errorMessage
                    ? payload.errorMessage
                    : 'Upload failed. Please try again.';
                showFeedback(message, 'danger');
            }
            finalizeUploadState();
        });

        xhr.addEventListener('error', () => {
            activeRequest = null;
            showFeedback('Upload failed. Please try again.', 'danger');
            finalizeUploadState();
        });

        xhr.addEventListener('abort', () => {
            activeRequest = null;
            showFeedback('Upload was cancelled.', 'info');
            finalizeUploadState();
        });

        xhr.send(formData);
    });

    function finalizeUploadState() {
        if (progressBar) {
            progressBar.classList.remove('bg-success');
            progressBar.classList.add('progress-bar-animated');
        }
        if (progressResetTimeoutId !== null) {
            window.clearTimeout(progressResetTimeoutId);
        }
        progressResetTimeoutId = window.setTimeout(() => {
            toggleProgress(false);
            progressResetTimeoutId = null;
        }, 400);
        if (uploadButton) {
            uploadButton.disabled = selectedFiles.length === 0;
            uploadButton.textContent = originalButtonText || FALLBACK_UPLOAD_BUTTON_TEXT;
        }
        if (fileInput) {
            fileInput.disabled = false;
            if (!supportsFileAssignment) {
                fileInput.value = '';
            }
        }
        if (triggerButton) {
            triggerButton.disabled = false;
        }
        updateHint(selectedFiles.length === 0 ? DEFAULT_HINT : `${selectedFiles.length} file${selectedFiles.length === 1 ? '' : 's'} ready for upload.`);
    }

    refreshSelectedFiles();
})();
