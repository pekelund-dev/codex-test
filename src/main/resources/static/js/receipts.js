(function () {
    const dropzone = document.getElementById('receipt-dropzone');
    const fileInput = document.getElementById('receipt-files');
    const triggerButton = document.getElementById('trigger-file-select');
    const selectedList = document.getElementById('selected-files');
    const uploadButton = document.getElementById('upload-button');
    const hint = document.getElementById('file-limit-hint');

    if (!dropzone || !fileInput || !selectedList || !uploadButton) {
        return;
    }

    const MAX_FILES = 10;

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

    function rebuildFileList(files) {
        const dataTransfer = new DataTransfer();
        files.forEach((file) => dataTransfer.items.add(file));
        fileInput.files = dataTransfer.files;
    }

    function refreshSelectedFiles() {
        const files = Array.from(fileInput.files);
        selectedList.innerHTML = '';

        if (files.length === 0) {
            uploadButton.disabled = true;
            if (hint) {
                hint.textContent = `You can add up to ${MAX_FILES} files per upload.`;
            }
            return;
        }

        files.forEach((file, index) => {
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
                files.splice(index, 1);
                rebuildFileList(files);
                refreshSelectedFiles();
            });

            item.append(details, removeButton);
            selectedList.appendChild(item);
        });

        uploadButton.disabled = false;
        if (hint) {
            hint.textContent = `${files.length} file${files.length === 1 ? '' : 's'} ready for upload.`;
        }
    }

    function handleFiles(newFiles) {
        if (!newFiles || newFiles.length === 0) {
            return;
        }

        const currentFiles = Array.from(fileInput.files);
        const merged = currentFiles.concat(Array.from(newFiles));

        if (merged.length > MAX_FILES) {
            merged.splice(MAX_FILES);
            if (hint) {
                hint.textContent = `Only the first ${MAX_FILES} files were added.`;
            }
        }

        rebuildFileList(merged);
        refreshSelectedFiles();
    }

    triggerButton?.addEventListener('click', () => fileInput.click());

    fileInput.addEventListener('change', (event) => {
        handleFiles(event.target.files);
        fileInput.value = '';
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
})();

