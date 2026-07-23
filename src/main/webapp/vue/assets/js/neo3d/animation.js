(function (window) {
    "use strict";

    function initGsapAnimations(gsap) {
        if (!gsap) return;
        if (window.ScrollTrigger) {
            gsap.registerPlugin(ScrollTrigger);
        }

        const intro = document.querySelectorAll(".brand-panel, .auth-panel, .topbar, .hero-panel, .command-panel, .chart-panel");
        gsap.from(intro, {
            opacity: 0,
            y: 34,
            rotateX: 4,
            duration: 1.05,
            ease: "power3.out",
            stagger: 0.09,
            clearProps: "transform"
        });

        const reveal = document.querySelectorAll(".live-card, .terminal-board, .trend-board, .rank-board, .panel, .stat-card, .arena-card, .market-stat, .reward, .loot-card");
        gsap.from(reveal, {
            opacity: 0,
            y: 18,
            duration: 0.78,
            ease: "power3.out",
            stagger: 0.035,
            delay: 0.16
        });

        document.querySelectorAll(".btn, .btn-primary, .action-btn, .combat-btn, .modal-btn").forEach(function (button) {
            button.addEventListener("mouseenter", function () {
                if (button.disabled) return;
                gsap.to(button, { y: -2, scale: 1.018, duration: 0.22, ease: "power2.out" });
            });
            button.addEventListener("mouseleave", function () {
                gsap.to(button, { y: 0, scale: 1, duration: 0.32, ease: "elastic.out(1, 0.55)" });
            });
            button.addEventListener("mousedown", function () {
                if (button.disabled) return;
                gsap.to(button, { scale: 0.972, duration: 0.08, ease: "power2.out" });
            });
            button.addEventListener("mouseup", function () {
                gsap.to(button, { scale: 1.012, duration: 0.16, ease: "power2.out" });
            });
        });

        document.querySelectorAll(".brand-panel, .auth-panel, .hero-panel, .command-panel, .panel, .arena-card, .stat-card").forEach(function (card) {
            card.addEventListener("mousemove", function (event) {
                const rect = card.getBoundingClientRect();
                const x = (event.clientX - rect.left) / Math.max(rect.width, 1) - 0.5;
                const y = (event.clientY - rect.top) / Math.max(rect.height, 1) - 0.5;
                card.style.setProperty("--tilt-x", (x * 100).toFixed(1) + "%");
                card.style.setProperty("--tilt-y", (y * 100).toFixed(1) + "%");
                gsap.to(card, {
                    rotationY: x * 2.4,
                    rotationX: -y * 1.8,
                    y: -2,
                    duration: 0.42,
                    ease: "power2.out",
                    transformPerspective: 1000,
                    overwrite: true
                });
            });
            card.addEventListener("mouseleave", function () {
                gsap.to(card, {
                    rotationY: 0,
                    rotationX: 0,
                    y: 0,
                    duration: 0.72,
                    ease: "elastic.out(1, 0.55)",
                    overwrite: true
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

    function updateScene(scene, camera, lights, mouse, elapsed, gsap) {
        const objects = scene.userData.objects;
        if (!objects) return;

        objects.auroraMaterial.uniforms.uTime.value = elapsed;
        objects.auroraMaterial.uniforms.uMouse.value.set(mouse.x, mouse.y);
        objects.coreMaterial.uniforms.uTime.value = elapsed;

        objects.starField.rotation.y = elapsed * 0.018 + (mouse.x - 0.5) * 0.12;
        objects.starField.rotation.x = (mouse.y - 0.5) * 0.08;

        objects.grid.position.z = -6.2 + (elapsed * 0.42) % 1.15;
        objects.grid.rotation.z = (mouse.x - 0.5) * 0.025;

        objects.core.rotation.y = elapsed * 0.18;
        objects.core.rotation.x = elapsed * 0.11;
        objects.core.position.x = 1.15 + (mouse.x - 0.5) * 0.7;
        objects.core.position.y = 0.72 + (mouse.y - 0.5) * 0.45 + Math.sin(elapsed * 0.62) * 0.08;

        if (objects.wireShell) {
            objects.wireShell.position.copy(objects.core.position);
            objects.wireShell.rotation.x = -elapsed * 0.08;
            objects.wireShell.rotation.y = elapsed * 0.14;
            objects.wireShell.rotation.z = elapsed * 0.055;
            objects.wireShell.material.opacity = 0.28 + Math.sin(elapsed * 0.9) * 0.08;
        }

        if (objects.beam && objects.beamMaterial) {
            objects.beam.position.x = objects.core.position.x;
            objects.beam.position.y = objects.core.position.y * 0.18;
            objects.beam.rotation.z = -0.18 + (mouse.x - 0.5) * 0.05;
            objects.beamMaterial.uniforms.uTime.value = elapsed;
        }

        objects.ringGroup.position.copy(objects.core.position);
        objects.ringGroup.rotation.y = elapsed * 0.12;
        objects.ringGroup.rotation.z = elapsed * 0.08;

        if (objects.portalGroup) {
            objects.portalGroup.rotation.z = elapsed * 0.035;
            objects.portalGroup.rotation.y = (mouse.x - 0.5) * 0.1;
            objects.portalGroup.children.forEach(function (portal, index) {
                portal.rotation.z = elapsed * (0.08 + index * 0.025);
                portal.material.opacity = 0.24 + Math.sin(elapsed * 0.62 + index) * 0.05;
            });
        }

        objects.bands.children.forEach(function (band) {
            band.material.opacity = 0.52 + Math.sin(elapsed * 0.8 + band.userData.offset) * 0.14;
            band.position.x = Math.sin(elapsed * 0.22 + band.userData.offset) * 0.24;
        });

        objects.floating.children.forEach(function (mesh, index) {
            mesh.rotation.x += 0.002 + index * 0.00015;
            mesh.rotation.y += 0.003 + index * 0.00012;
            mesh.position.y += Math.sin(elapsed * mesh.userData.floatSpeed + mesh.userData.floatPhase) * 0.0028;
        });

        window.GE3D.Camera.updateCamera(camera, mouse, elapsed, gsap);
        if (lights && lights.update) {
            lights.update(elapsed, mouse);
        }
    }

    window.GE3D = window.GE3D || {};
    window.GE3D.Animation = {
        initGsapAnimations: initGsapAnimations,
        updateScene: updateScene
    };
})(window);
