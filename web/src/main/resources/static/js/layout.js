const supportsBootstrapCollapse = () =>
    typeof window.bootstrap !== 'undefined'
    && window.bootstrap !== null
    && typeof window.bootstrap.Collapse === 'function';

const DARK_MODE_KEY = 'pklnd-theme';
const DARK_THEME = 'dark';
const LIGHT_THEME = 'light';

const applyTheme = (theme) => {
    document.documentElement.setAttribute('data-bs-theme', theme);
};

const setupDarkModeToggle = () => {
    const savedTheme = localStorage.getItem(DARK_MODE_KEY) || LIGHT_THEME;
    applyTheme(savedTheme);

    const toggle = document.getElementById('dark-mode-toggle');
    if (!toggle) {
        return;
    }

    toggle.addEventListener('click', () => {
        const current = document.documentElement.getAttribute('data-bs-theme') || LIGHT_THEME;
        const next = current === DARK_THEME ? LIGHT_THEME : DARK_THEME;
        localStorage.setItem(DARK_MODE_KEY, next);
        applyTheme(next);
    });
};

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
    setupDarkModeToggle();
};

const initializePageLayout = () => {
    setupPageLayout();
};

if (document.readyState === 'loading') {
    window.addEventListener('DOMContentLoaded', initializePageLayout);
} else {
    initializePageLayout();
}
