(function (window, document) {
    "use strict";

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

    function startFallback() {
        if (window.GE3D_DEBUG || !window.THREE) return;

        const THREE = window.THREE;
        const root = ensureRoot();
        const scene = new THREE.Scene();
        const camera = new THREE.PerspectiveCamera(52, window.innerWidth / Math.max(window.innerHeight, 1), 0.1, 100);
        camera.position.set(0, 0.8, 9);

        const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: "high-performance" });
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
        renderer.setSize(window.innerWidth, window.innerHeight);
        renderer.setClearColor(0x000000, 0);
        root.appendChild(renderer.domElement);

        scene.add(new THREE.AmbientLight(0x8fcfff, 0.65));
        const pointA = new THREE.PointLight(0x69e8ff, 3.5, 24);
        pointA.position.set(-3, 2, 4);
        scene.add(pointA);
        const pointB = new THREE.PointLight(0xff5ca8, 2.8, 22);
        pointB.position.set(4, -1, 3);
        scene.add(pointB);

        const group = new THREE.Group();
        group.position.set(1.2, 0.1, -3.4);
        scene.add(group);

        const coreMaterial = new THREE.MeshStandardMaterial({
            color: 0x69e8ff,
            emissive: 0x258cff,
            emissiveIntensity: 1.8,
            metalness: 0.35,
            roughness: 0.18,
            transparent: true,
            opacity: 0.82
        });
        const core = new THREE.Mesh(new THREE.SphereGeometry(1.35, 48, 28), coreMaterial);
        group.add(core);

        const wire = new THREE.Mesh(
            new THREE.IcosahedronGeometry(2.3, 3),
            new THREE.MeshBasicMaterial({
                color: 0x73ffca,
                wireframe: true,
                transparent: true,
                opacity: 0.42,
                blending: THREE.AdditiveBlending
            })
        );
        group.add(wire);

        [2.8, 3.65, 4.55].forEach(function (radius, index) {
            const ring = new THREE.Mesh(
                new THREE.TorusGeometry(radius, 0.025, 8, 180),
                new THREE.MeshBasicMaterial({
                    color: index === 1 ? 0xa987ff : 0x69e8ff,
                    transparent: true,
                    opacity: 0.48,
                    blending: THREE.AdditiveBlending
                })
            );
            ring.rotation.set(1.2 + index * 0.24, 0.3 + index * 0.18, index * 0.55);
            group.add(ring);
        });

        const positions = new Float32Array(1200 * 3);
        for (let i = 0; i < 1200; i++) {
            const i3 = i * 3;
            positions[i3] = (Math.random() - 0.5) * 26;
            positions[i3 + 1] = (Math.random() - 0.5) * 12;
            positions[i3 + 2] = -Math.random() * 24;
        }
        const starsGeometry = new THREE.BufferGeometry();
        starsGeometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
        const stars = new THREE.Points(starsGeometry, new THREE.PointsMaterial({
            color: 0x8fefff,
            size: 0.045,
            transparent: true,
            opacity: 0.9,
            blending: THREE.AdditiveBlending,
            depthWrite: false
        }));
        scene.add(stars);

        document.body.classList.add("ge-webgl-ready");
        window.GE3D_DEBUG = { renderer: renderer, scene: scene, camera: camera, objects: { fallback: group } };

        window.addEventListener("resize", function () {
            camera.aspect = window.innerWidth / Math.max(window.innerHeight, 1);
            camera.updateProjectionMatrix();
            renderer.setSize(window.innerWidth, window.innerHeight);
        }, { passive: true });

        let mouseX = 0;
        let mouseY = 0;
        window.addEventListener("mousemove", function (event) {
            mouseX = event.clientX / Math.max(window.innerWidth, 1) - 0.5;
            mouseY = event.clientY / Math.max(window.innerHeight, 1) - 0.5;
        }, { passive: true });

        function animate(time) {
            const t = time * 0.001;
            core.rotation.y = t * 0.25;
            core.rotation.x = t * 0.14;
            wire.rotation.y = -t * 0.18;
            wire.rotation.z = t * 0.08;
            group.rotation.y = mouseX * 0.18;
            group.rotation.x = -mouseY * 0.1;
            group.children.forEach(function (child, index) {
                if (child.geometry && child.geometry.type === "TorusGeometry") {
                    child.rotation.z += 0.003 + index * 0.0008;
                }
            });
            stars.rotation.y = t * 0.018 + mouseX * 0.05;
            camera.lookAt(mouseX * 0.8, -mouseY * 0.5, -2);
            renderer.render(scene, camera);
            requestAnimationFrame(animate);
        }

        requestAnimationFrame(animate);
    }

    ready(function () {
        setTimeout(startFallback, 500);
    });
})(window, document);
