/*
 * Face capture and feature extraction (Module 2 - biometric authentication).
 * Uses face-api.js: detects the face, finds 68 landmarks and computes a 128-value
 * face descriptor, which is submitted to the server as the biometric template.
 */
(function () {
    'use strict';

    const root = document.getElementById('faceCapture');
    if (!root) return;

    const video = document.getElementById('faceVideo');
    const preview = document.getElementById('facePreview');
    const placeholder = document.getElementById('facePlaceholder');
    const status = document.getElementById('faceStatus');
    const btnCamera = document.getElementById('btnCamera');
    const btnCapture = document.getElementById('btnCapture');
    const fileInput = document.getElementById('faceFile');
    const descriptorInput = document.getElementById('faceDescriptor');
    const imageInput = document.getElementById('faceImage'); // only present on the registration form
    const submit = root.closest('form').querySelector('[data-face-submit]');

    const MAX_SIDE = 480;
    let stream = null;

    function setStatus(text, kind) {
        status.textContent = text;
        status.className = 'face-status' + (kind ? ' ' + kind : '');
    }

    function show(element) {
        [video, preview, placeholder].forEach(function (el) { el.hidden = el !== element; });
    }

    function clearResult() {
        descriptorInput.value = '';
        if (imageInput) imageInput.value = '';
        submit.disabled = true;
    }

    const modelUrl = root.dataset.models;
    /** Uses the GPU (WebGL) when available, otherwise falls back to the slower CPU backend. */
    async function initBackend() {
        let ok = false;
        try {
            ok = await faceapi.tf.setBackend('webgl');
        } catch (e) {
            ok = false;
        }
        if (!ok) await faceapi.tf.setBackend('cpu');
        await faceapi.tf.ready();
    }

    const modelsReady = initBackend().then(function () {
        return Promise.all([
            faceapi.nets.tinyFaceDetector.loadFromUri(modelUrl),
            faceapi.nets.faceLandmark68Net.loadFromUri(modelUrl),
            faceapi.nets.faceRecognitionNet.loadFromUri(modelUrl)
        ]);
    });
    modelsReady.catch(function () {
        setStatus('Could not load the face recognition models. Reload the page and try again.', 'error');
    });

    function stopCamera() {
        if (stream) {
            stream.getTracks().forEach(function (track) { track.stop(); });
            stream = null;
        }
        video.srcObject = null;
        btnCapture.disabled = true;
        btnCamera.textContent = 'Start camera';
    }

    async function startCamera() {
        clearResult();
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            setStatus('Camera is not available in this browser. Upload a photo instead.', 'error');
            return;
        }
        try {
            stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user', width: 640, height: 480 }, audio: false });
            video.srcObject = stream;
            await video.play();
            show(video);
            btnCapture.disabled = false;
            btnCamera.textContent = 'Stop camera';
            setStatus('Look straight at the camera and press Capture.', '');
        } catch (e) {
            setStatus('Camera access was blocked or no camera was found. Allow access or upload a photo.', 'error');
        }
    }

    /** Draws the source (video frame or image) onto a canvas no larger than MAX_SIDE. */
    function toCanvas(source, width, height) {
        const scale = Math.min(1, MAX_SIDE / Math.max(width, height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(width * scale);
        canvas.height = Math.round(height * scale);
        canvas.getContext('2d').drawImage(source, 0, 0, canvas.width, canvas.height);
        return canvas;
    }

    async function analyse(canvas) {
        clearResult();
        preview.src = canvas.toDataURL('image/jpeg', 0.85);
        show(preview);
        setStatus('Analysing face…', 'busy');
        try {
            await modelsReady;
            const options = new faceapi.TinyFaceDetectorOptions({ inputSize: 416, scoreThreshold: 0.5 });
            const faces = await faceapi.detectAllFaces(canvas, options).withFaceLandmarks().withFaceDescriptors();
            if (faces.length === 0) {
                setStatus('No face detected. Use good lighting, face the camera and try again.', 'error');
                return;
            }
            if (faces.length > 1) {
                setStatus('More than one face detected. Only you should be in the picture.', 'error');
                return;
            }
            descriptorInput.value = Array.from(faces[0].descriptor).map(function (v) { return v.toFixed(6); }).join(',');
            if (imageInput) imageInput.value = preview.src;
            submit.disabled = false;
            setStatus('Face captured successfully. Biometric template is ready.', 'ok');
        } catch (e) {
            setStatus('Face analysis failed. Please try again.', 'error');
        }
    }

    btnCamera.addEventListener('click', function () {
        if (stream) {
            stopCamera();
            show(placeholder);
            setStatus('Camera stopped.', '');
        } else {
            startCamera();
        }
    });

    btnCapture.addEventListener('click', function () {
        if (!stream || !video.videoWidth) return;
        const canvas = toCanvas(video, video.videoWidth, video.videoHeight);
        stopCamera();
        analyse(canvas);
    });

    fileInput.addEventListener('change', function () {
        const file = fileInput.files[0];
        fileInput.value = '';
        if (!file) return;
        stopCamera();
        const image = new Image();
        image.onload = function () {
            analyse(toCanvas(image, image.naturalWidth, image.naturalHeight));
            URL.revokeObjectURL(image.src);
        };
        image.onerror = function () {
            clearResult();
            setStatus('That file is not a valid image.', 'error');
        };
        image.src = URL.createObjectURL(file);
    });

    window.addEventListener('pagehide', stopCamera);
})();
