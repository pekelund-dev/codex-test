const supportsBootstrapCollapse = () =>
    typeof window.bootstrap !== 'undefined'
    && window.bootstrap !== null
    && typeof window.bootstrap.Collapse === 'function';

const setupSplashScreen = () => {
    const splash = document.getElementById('splash-screen');
    if (!splash) {
        return;
    }

    splash.classList.add('hidden');
    setTimeout(() => splash.remove(), 600);
};

const setupNavbarFallbackToggle = () => {
    const toggler = document.querySelector('.navbar-toggler');
    if (!toggler) {
        return;
    }

    const targetSelector = toggler.getAttribute('data-bs-target')
        || toggler.getAttribute('aria-controls');
    if (!targetSelector) {
        return;
    }

    const target = document.querySelector(targetSelector);
    if (!target) {
        return;
    }

    toggler.addEventListener('click', (event) => {
        if (supportsBootstrapCollapse()) {
            return;
        }

        event.preventDefault();
        const isShown = target.classList.toggle('show');
        toggler.classList.toggle('collapsed', !isShown);
        toggler.setAttribute('aria-expanded', isShown ? 'true' : 'false');
    });

    target.querySelectorAll('a.nav-link, button').forEach((element) => {
        element.addEventListener('click', () => {
            if (supportsBootstrapCollapse()) {
                return;
            }

            if (!target.classList.contains('show')) {
                return;
            }

            target.classList.remove('show');
            toggler.classList.add('collapsed');
            toggler.setAttribute('aria-expanded', 'false');
        });
    });
};

const setupPageLayout = () => {
    setupSplashScreen();
    setupNavbarFallbackToggle();
};

window.addEventListener('load', () => {
    setupPageLayout();
});
