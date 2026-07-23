(function () {
    "use strict";

    window.GE_NEO_VISUALS_ENABLED = true;

    const reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const state = {
        raf: 0,
        renderer: null,
        scene: null,
        camera: null,
        materials: [],
        geometries: [],
        uniforms: null,
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

    function createShaderMaterial() {
        state.uniforms = {
            uTime: { value: 0 },
            uResolution: { value: new THREE.Vector2(window.innerWidth, window.innerHeight) },
            uMouse: { value: new THREE.Vector2(0.5, 0.5) }
        };

        return new THREE.ShaderMaterial({
            transparent: true,
            depthWrite: false,
            uniforms: state.uniforms,
            vertexShader: [
                "varying vec2 vUv;",
                "void main() {",
                "  vUv = uv;",
                "  gl_Position = vec4(position.xy, 0.0, 1.0);",
                "}"
            ].join("\n"),
            fragmentShader: [
                "precision highp float;",
                "uniform float uTime;",
                "uniform vec2 uResolution;",
                "uniform vec2 uMouse;",
                "varying vec2 vUv;",
                "float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }",
                "float noise(vec2 p) {",
                "  vec2 i = floor(p);",
                "  vec2 f = fract(p);",
                "  vec2 u = f * f * (3.0 - 2.0 * f);",
                "  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);",
                "}",
                "float fbm(vec2 p) {",
                "  float v = 0.0;",
                "  float a = 0.5;",
                "  for (int i = 0; i < 5; i++) {",
                "    v += a * noise(p);",
                "    p *= 2.04;",
                "    a *= 0.5;",
                "  }",
                "  return v;",
                "}",
                "void main() {",
                "  vec2 uv = vUv;",
                "  vec2 aspect = vec2(uResolution.x / max(uResolution.y, 1.0), 1.0);",
                "  vec2 p = (uv - 0.5) * aspect;",
                "  float t = uTime * 0.08;",
                "  float flow = fbm(vec2(p.x * 1.8 + t, p.y * 2.4 - t));",
                "  float wave = sin((p.x + flow * 0.45 + uTime * 0.09) * 6.0) * 0.5 + 0.5;",
                "  float aurora = smoothstep(0.18, 0.94, wave) * smoothstep(0.72, 0.06, abs(p.y + 0.16 + flow * 0.18));",
                "  float plasma = smoothstep(0.62, 0.98, fbm(p * 3.4 + vec2(t * 1.4, -t)));",
                "  float cursor = smoothstep(0.34, 0.0, distance(uv, uMouse));",
                "  vec3 cyan = vec3(0.38, 0.91, 1.0);",
                "  vec3 violet = vec3(0.66, 0.47, 1.0);",
                "  vec3 mint = vec3(0.43, 1.0, 0.78);",
                "  vec3 rose = vec3(1.0, 0.32, 0.68);",
                "  vec3 color = cyan * aurora + violet * plasma * 0.55 + mint * flow * 0.08 + rose * cursor * 0.16;",
                "  float alpha = clamp(aurora * 0.32 + plasma * 0.11 + cursor * 0.18, 0.0, 0.52);",
                "  gl_FragColor = vec4(color, alpha * 1.32);",
                "}"
            ].join("\n")
        });
    }

    function createParticles(count) {
        const positions = new Float32Array(count * 3);
        const colors = new Float32Array(count * 3);
        for (let i = 0; i < count; i++) {
            const i3 = i * 3;
            positions[i3] = (Math.random() - 0.5) * 24;
            positions[i3 + 1] = (Math.random() - 0.5) * 14;
            positions[i3 + 2] = (Math.random() - 0.5) * 16;
            const palette = Math.random();
            colors[i3] = palette > 0.66 ? 0.78 : 0.38;
            colors[i3 + 1] = palette > 0.33 ? 0.9 : 0.48;
            colors[i3 + 2] = 1.0;
        }

        const geometry = new THREE.BufferGeometry();
        geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
        geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
        state.geometries.push(geometry);

        const material = new THREE.PointsMaterial({
            size: 0.038,
            vertexColors: true,
            transparent: true,
            opacity: 0.9,
            depthWrite: false,
            blending: THREE.AdditiveBlending
        });
        state.materials.push(material);
        return new THREE.Points(geometry, material);
    }

    function createGrid() {
        const helper = new THREE.GridHelper(28, 42, 0x69e8ff, 0x3d3a72);
        helper.position.y = -3.2;
        helper.rotation.x = Math.PI * 0.04;
        helper.material.transparent = true;
        helper.material.opacity = 0.28;
        state.materials.push(helper.material);
        return helper;
    }

    function createOrbitLines() {
        const group = new THREE.Group();
        const colors = [0x69e8ff, 0xa987ff, 0x73ffca];

        colors.forEach(function (color, index) {
            const curve = new THREE.EllipseCurve(0, 0, 3.4 + index * 0.46, 1.08 + index * 0.22, 0, Math.PI * 2, false, 0);
            const points = curve.getPoints(160).map(function (point) {
                return new THREE.Vector3(point.x, point.y, Math.sin(point.x * 1.2 + index) * 0.18);
            });
            const geometry = new THREE.BufferGeometry().setFromPoints(points);
            const material = new THREE.LineBasicMaterial({
                color: color,
                transparent: true,
                opacity: 0.24 - index * 0.035,
                blending: THREE.AdditiveBlending
            });
            const line = new THREE.LineLoop(geometry, material);
            line.rotation.x = 0.78 + index * 0.24;
            line.rotation.y = 0.22 + index * 0.16;
            group.add(line);
            state.geometries.push(geometry);
            state.materials.push(material);
        });

        group.position.set(3.8, 0.85, -1.1);
        return group;
    }

    function initThree() {
        if (reduceMotion || !window.THREE) return;

        const root = ensureRoot();
        const scene = new THREE.Scene();
        const camera = new THREE.PerspectiveCamera(55, window.innerWidth / Math.max(window.innerHeight, 1), 0.1, 80);
        camera.position.set(0, 0, 10);

        const renderer = new THREE.WebGLRenderer({
            antialias: false,
            alpha: true,
            powerPreference: "high-performance"
        });
        renderer.setClearColor(0x000000, 0);
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.6));
        renderer.setSize(window.innerWidth, window.innerHeight);
        root.appendChild(renderer.domElement);

        const shaderMaterial = createShaderMaterial();
        const shaderPlane = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), shaderMaterial);
        shaderPlane.frustumCulled = false;
        shaderPlane.renderOrder = -10;
        scene.add(shaderPlane);
        state.materials.push(shaderMaterial);
        state.geometries.push(shaderPlane.geometry);

        const particles = createParticles(window.innerWidth < 720 ? 620 : 1180);
        scene.add(particles);

        const grid = createGrid();
        scene.add(grid);

        const coreGeometry = new THREE.IcosahedronGeometry(1.35, 2);
        const coreMaterial = new THREE.MeshBasicMaterial({
            color: 0x69e8ff,
            wireframe: true,
            transparent: true,
            opacity: 0.34,
            blending: THREE.AdditiveBlending
        });
        const core = new THREE.Mesh(coreGeometry, coreMaterial);
        core.position.set(3.8, 0.85, -1.2);
        scene.add(core);
        state.geometries.push(coreGeometry);
        state.materials.push(coreMaterial);

        const orbitLines = createOrbitLines();
        scene.add(orbitLines);

        const clock = new THREE.Clock();
        state.renderer = renderer;
        state.scene = scene;
        state.camera = camera;

        state.mouseHandler = function (event) {
            state.targetMouse.x = event.clientX / Math.max(window.innerWidth, 1);
            state.targetMouse.y = 1 - event.clientY / Math.max(window.innerHeight, 1);
            document.documentElement.style.setProperty("--ge-mouse-x", event.clientX + "px");
            document.documentElement.style.setProperty("--ge-mouse-y", event.clientY + "px");
        };

        state.resizeHandler = function () {
            camera.aspect = window.innerWidth / Math.max(window.innerHeight, 1);
            camera.updateProjectionMatrix();
            renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.6));
            renderer.setSize(window.innerWidth, window.innerHeight);
            if (state.uniforms) {
                state.uniforms.uResolution.value.set(window.innerWidth, window.innerHeight);
            }
        };

        window.addEventListener("mousemove", state.mouseHandler, { passive: true });
        window.addEventListener("resize", state.resizeHandler, { passive: true });

        function animate() {
            const elapsed = clock.getElapsedTime();
            state.mouse.x += (state.targetMouse.x - state.mouse.x) * 0.06;
            state.mouse.y += (state.targetMouse.y - state.mouse.y) * 0.06;
            if (state.uniforms) {
                state.uniforms.uTime.value = elapsed;
                state.uniforms.uMouse.value.set(state.mouse.x, state.mouse.y);
            }

            particles.rotation.y = elapsed * 0.025 + (state.mouse.x - 0.5) * 0.18;
            particles.rotation.x = (state.mouse.y - 0.5) * 0.12;
            grid.position.z = (elapsed * 0.32) % 1.2;
            core.rotation.x = elapsed * 0.18;
            core.rotation.y = elapsed * 0.28;
            core.position.x = 3.8 + (state.mouse.x - 0.5) * 0.62;
            core.position.y = 0.85 + (state.mouse.y - 0.5) * 0.48;
            orbitLines.rotation.z = elapsed * 0.09;
            orbitLines.rotation.y = (state.mouse.x - 0.5) * 0.22;
            orbitLines.position.x = core.position.x;
            orbitLines.position.y = core.position.y;

            renderer.render(scene, camera);
            state.raf = requestAnimationFrame(animate);
        }

        animate();
    }

    function initGsap() {
        if (reduceMotion || !window.gsap) return;
        if (window.ScrollTrigger) {
            gsap.registerPlugin(ScrollTrigger);
        }

        const intro = document.querySelectorAll(".topbar, .brand-panel, .auth-panel, .hero-panel, .command-panel, .chart-panel");
        gsap.from(intro, {
            opacity: 0,
            y: 24,
            duration: 0.9,
            ease: "power3.out",
            stagger: 0.08,
            clearProps: "transform"
        });

        const revealTargets = document.querySelectorAll(".panel, .stat-card, .arena-card, .market-stat, .live-card, .reward, .loot-card");
        if (window.ScrollTrigger) {
            gsap.from(revealTargets, {
                opacity: 0,
                y: 18,
                duration: 0.72,
                ease: "power3.out",
                stagger: 0.035,
                scrollTrigger: {
                    trigger: document.body,
                    start: "top top",
                    toggleActions: "play none none none"
                }
            });
        } else {
            gsap.from(revealTargets, {
                opacity: 0,
                y: 18,
                duration: 0.72,
                ease: "power3.out",
                stagger: 0.035
            });
        }

        document.querySelectorAll(".btn, .btn-primary, .action-btn, .combat-btn, .modal-btn").forEach(function (button) {
            button.addEventListener("mouseenter", function () {
                if (button.disabled) return;
                gsap.to(button, { y: -2, scale: 1.018, duration: 0.22, ease: "power2.out" });
            });
            button.addEventListener("mouseleave", function () {
                gsap.to(button, { y: 0, scale: 1, duration: 0.28, ease: "elastic.out(1, 0.55)" });
            });
            button.addEventListener("mousedown", function () {
                if (button.disabled) return;
                gsap.to(button, { scale: 0.975, duration: 0.08, ease: "power2.out" });
            });
            button.addEventListener("mouseup", function () {
                gsap.to(button, { scale: 1.018, duration: 0.14, ease: "power2.out" });
            });
        });

        document.querySelectorAll(".brand-panel, .auth-panel, .hero-panel, .command-panel, .panel, .arena-card, .stat-card").forEach(function (card) {
            card.addEventListener("mousemove", function (event) {
                const rect = card.getBoundingClientRect();
                const x = (event.clientX - rect.left) / Math.max(rect.width, 1) - 0.5;
                const y = (event.clientY - rect.top) / Math.max(rect.height, 1) - 0.5;
                gsap.to(card, {
                    rotationY: x * 2.2,
                    rotationX: -y * 1.8,
                    y: -2,
                    duration: 0.42,
                    ease: "power2.out",
                    transformPerspective: 900
                });
            });
            card.addEventListener("mouseleave", function () {
                gsap.to(card, {
                    rotationY: 0,
                    rotationX: 0,
                    y: 0,
                    duration: 0.72,
                    ease: "elastic.out(1, 0.55)"
                });
            });
        });

        document.querySelectorAll("a[href^='#']").forEach(function (anchor) {
            anchor.addEventListener("click", function (event) {
                const target = document.querySelector(anchor.getAttribute("href"));
                if (!target) return;
                event.preventDefault();
                target.scrollIntoView({ behavior: "smooth", block: "start" });
            });
        });
    }

    function destroy() {
        cancelAnimationFrame(state.raf);
        if (state.resizeHandler) window.removeEventListener("resize", state.resizeHandler);
        if (state.mouseHandler) window.removeEventListener("mousemove", state.mouseHandler);
        state.materials.forEach(function (material) { if (material.dispose) material.dispose(); });
        state.geometries.forEach(function (geometry) { if (geometry.dispose) geometry.dispose(); });
        if (state.renderer) {
            state.renderer.dispose();
            if (state.renderer.domElement && state.renderer.domElement.parentNode) {
                state.renderer.domElement.parentNode.removeChild(state.renderer.domElement);
            }
        }
    }

    ready(function () {
        ensureRoot();
        initThree();
        initGsap();
    });

    window.addEventListener("pagehide", destroy, { once: true });
})();
