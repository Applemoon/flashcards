// Тема и звук — настройки браузера, хранятся в localStorage. Скрипт подключается в <head> без defer:
// data-атрибуты встают до первой отрисовки, и страница не мигает чужой темой.
(function() {
    const root = document.documentElement;
    try {
        root.dataset.theme = localStorage.getItem('theme') || 'dark';
        root.dataset.sound = localStorage.getItem('sound') || 'on';
    } catch (e) {
        root.dataset.theme = 'dark';
        root.dataset.sound = 'on';
    }
})();

function togglePref(name, a, b) {
    const value = document.documentElement.dataset[name] === a ? b : a;
    document.documentElement.dataset[name] = value;
    try { localStorage.setItem(name, value); } catch (e) {}
}

function toggleTheme() { togglePref('theme', 'light', 'dark'); }

function toggleSound() { togglePref('sound', 'off', 'on'); }

function soundOn() { return document.documentElement.dataset.sound !== 'off'; }
