const fallbackScripts = ['/js/layout.js', '/js/table-sort.js'];

fallbackScripts.forEach((src) => {
    const script = document.createElement('script');
    script.src = src;
    script.defer = true;
    document.head.appendChild(script);
});
