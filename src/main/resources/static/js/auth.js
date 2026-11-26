/* Login and registration page behaviour: show/hide password and the registration step wizard. */
(function () {
    'use strict';

    document.querySelectorAll('[data-toggle-password]').forEach(function (button) {
        const input = document.getElementById(button.dataset.togglePassword);
        button.addEventListener('click', function () {
            const reveal = input.type === 'password';
            input.type = reveal ? 'text' : 'password';
            button.textContent = reveal ? 'Hide' : 'Show';
            button.setAttribute('aria-label', reveal ? 'Hide password' : 'Show password');
        });
    });

    const form = document.getElementById('registerForm');
    if (!form) return;

    const panels = Array.from(form.querySelectorAll('[data-step]'));
    const indicators = Array.from(document.querySelectorAll('[data-step-ind]'));
    const password = document.getElementById('password');
    const confirm = document.getElementById('confirmPassword');
    let current = 0;

    function show(index, focus) {
        current = index;
        panels.forEach(function (panel, i) { panel.hidden = i !== index; });
        indicators.forEach(function (item, i) {
            item.classList.toggle('active', i === index);
            item.classList.toggle('done', i < index);
            if (i === index) item.setAttribute('aria-current', 'step'); else item.removeAttribute('aria-current');
        });
        if (focus) {
            const first = panels[index].querySelector('input:not([type=hidden]), button');
            if (first) first.focus();
        }
    }

    function checkPasswords() {
        confirm.setCustomValidity(confirm.value && confirm.value !== password.value ? 'Passwords do not match.' : '');
    }
    password.addEventListener('input', checkPasswords);
    confirm.addEventListener('input', checkPasswords);

    /** Validates only the fields of the visible step. */
    function stepValid() {
        const fields = panels[current].querySelectorAll('input, textarea, select');
        for (let i = 0; i < fields.length; i++) {
            if (!fields[i].checkValidity()) {
                fields[i].reportValidity();
                return false;
            }
        }
        return true;
    }

    form.querySelectorAll('[data-next]').forEach(function (button) {
        button.addEventListener('click', function () {
            if (stepValid()) show(current + 1, true);
        });
    });
    form.querySelectorAll('[data-back]').forEach(function (button) {
        button.addEventListener('click', function () { show(current - 1, true); });
    });

    // Pressing Enter in an earlier step moves forward instead of submitting
    form.addEventListener('submit', function (event) {
        if (current < panels.length - 1) {
            event.preventDefault();
            if (stepValid()) show(current + 1, true);
        }
    });

    show(0, false);
})();
