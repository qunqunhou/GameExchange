(function (window, document) {
    "use strict";

    window.GE_NEO_VISUALS_ENABLED = true;

    const reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const state = {
        renderer: null,
        composer: null,
        scene: null,
        camera: null,
        lights: null,
        clock: null,
        raf: 0,
        materials: [],
        geometries: [],
        textures: [],
        mouse: { x: 0.5, y: 0.5 },
        targetMouse: { x: 0.5, y: 0.5 },
        resizeHandler: null,
        mouseHandler: null
    };

    function ready(callback) {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", callback, { once: true });
        } else {
            callback();
        }
    }

    function ensureRoot() {
        let root = document.querySelector(".ge-visual-root");
        if (!root) {
            root = document.createElement("div");
            root.className = "ge-visual-root";
            root.setAttribute("aria-hidden", "true");
            document.body.insertBefore(root, document.body.firstChild);
        }
        return root;
    }

    function createComposer(THREE, renderer, scene, camera) {
        if (document.body && document.body.classList.contains("ge-direct-render")) {
            return null;
        }

        if (!THREE.EffectComposer || !THREE.RenderPass || !THREE.UnrealBloomPass) {
            return window.GE3D.PostFX ? window.GE3D.PostFX.createPostFX(THREE, renderer, scene, camera) : null;
        }

        const composer = new THREE.EffectComposer(renderer);
        composer.addPass(new THREE.RenderPass(scene, camera));

        const bloom = new THREE.UnrealBloomPass(
            new THREE.Vector2(window.innerWidth, window.innerHeight),
            window.innerWidth < 720 ? 0.42 : 0.58,
            0.62,
            0.18
        );
        composer.addPass(bloom);
        state.bloomPass = bloom;

        if (THREE.FilmPass) {
            const film = new THREE.FilmPass(0.18, 0.18, 648, false);
            film.renderToScreen = !THREE.ShaderPass;
            composer.addPass(film);
            state.filmPass = film;
        }

        if (THREE.ShaderPass && THREE.FXAAShader) {
            const fxaa = new THREE.ShaderPass(THREE.FXAAShader);
            fxaa.material.uniforms.resolution.value.set(1 / window.innerWidth, 1 / window.innerHeight);
            composer.addPass(fxaa);
            state.fxaaPass = fxaa;
        }

        if (THREE.BokehPass && window.innerWidth >= 900) {
            const bokeh = new THREE.BokehPass(scene, camera, {
                focus: 8.0,
                aperture: 0.00009,
                maxblur: 0.006,
                width: window.innerWidth,
                height: window.innerHeight
            });
            composer.addPass(bokeh);
            state.bokehPass = bokeh;
        }

        return composer;
    }

    function initThree() {
        if (reduceMotion) return;
        if (!window.THREE || !window.GE3D || !window.GE3D.Scene || !window.GE3D.Camera || !window.GE3D.Lights) {
            setTimeout(initThree, 80);
            return;
        }

        const THREE = window.THREE;
        const root = ensureRoot();
        const isMobile = window.innerWidth < 720;
        const renderer = new THREE.WebGLRenderer({
            antialias: false,
            alpha: true,
            powerPreference: "high-performance"
        });
        renderer.setClearColor(0x000000, 0);
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, isMobile ? 1.25 : 1.65));
        renderer.setSize(window.innerWidth, window.innerHeight);
        renderer.outputColorSpace = THREE.SRGBColorSpace;
        renderer.toneMapping = THREE.ACESFilmicToneMapping;
        renderer.toneMappingExposure = 1.05;
        root.appendChild(renderer.domElement);

        const registry = {
            materials: state.materials,
            geometries: state.geometries,
            textures: state.textures
        };
        const scene = window.GE3D.Scene.createScene(THREE, registry, { isMobile: isMobile });
        const camera = window.GE3D.Camera.createCamera(THREE);
        const lights = window.GE3D.Lights.createLights(THREE, scene);

        state.renderer = renderer;
        state.scene = scene;
        state.camera = camera;
        state.lights = lights;
        state.clock = new THREE.Clock();
        window.GE3D_DEBUG = {
            renderer: renderer,
            scene: scene,
            camera: camera,
            objects: scene.userData.objects
        };
        document.body.classList.add("ge-webgl-ready");

        state.mouseHandler = function (event) {
            state.targetMouse.x = event.clientX / Math.max(window.innerWidth, 1);
            state.targetMouse.y = 1 - event.clientY / Math.max(window.innerHeight, 1);
            document.documentElement.style.setProperty("--ge-mouse-x", event.clientX + "px");
            document.documentElement.style.setProperty("--ge-mouse-y", event.clientY + "px");
        };

        state.resizeHandler = function () {
            const mobile = window.innerWidth < 720;
            camera.aspect = window.innerWidth / Math.max(window.innerHeight, 1);
            camera.updateProjectionMatrix();
            renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, mobile ? 1.25 : 1.65));
            renderer.setSize(window.innerWidth, window.innerHeight);
            if (state.composer) state.composer.setSize(window.innerWidth, window.innerHeight);
            if (state.fxaaPass) state.fxaaPass.material.uniforms.resolution.value.set(1 / window.innerWidth, 1 / window.innerHeight);
        };

        window.addEventListener("mousemove", state.mouseHandler, { passive: true });
        window.addEventListener("resize", state.resizeHandler, { passive: true });

        state.composer = createComposer(THREE, renderer, scene, camera);
        animate();
    }

    function animate() {
        const elapsed = state.clock.getElapsedTime();
        state.mouse.x += (state.targetMouse.x - state.mouse.x) * 0.055;
        state.mouse.y += (state.targetMouse.y - state.mouse.y) * 0.055;

        window.GE3D.Animation.updateScene(state.scene, state.camera, state.lights, state.mouse, elapsed, window.gsap);

        if (state.composer) {
            state.composer.render(elapsed);
        } else {
            state.renderer.render(state.scene, state.camera);
        }
        state.raf = requestAnimationFrame(animate);
    }

    function init() {
        ensureRoot();
        if (!reduceMotion) {
            initThree();
            if (window.GE3D && window.GE3D.Animation) {
                window.GE3D.Animation.initGsapAnimations(window.gsap);
            }
        }
    }

    function destroy() {
        cancelAnimationFrame(state.raf);
        if (state.resizeHandler) window.removeEventListener("resize", state.resizeHandler);
        if (state.mouseHandler) window.removeEventListener("mousemove", state.mouseHandler);
        state.materials.forEach(function (material) { if (material && material.dispose) material.dispose(); });
        state.geometries.forEach(function (geometry) { if (geometry && geometry.dispose) geometry.dispose(); });
        state.textures.forEach(function (texture) { if (texture && texture.dispose) texture.dispose(); });
        if (state.composer && state.composer.dispose) state.composer.dispose();
        if (state.renderer) {
            state.renderer.dispose();
            if (state.renderer.domElement && state.renderer.domElement.parentNode) {
                state.renderer.domElement.parentNode.removeChild(state.renderer.domElement);
            }
        }
    }

    ready(init);
    window.addEventListener("pagehide", destroy, { once: true });
})(window, document);
