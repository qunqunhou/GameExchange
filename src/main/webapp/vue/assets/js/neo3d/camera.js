(function (window) {
    "use strict";

    function createCamera(THREE) {
        const camera = new THREE.PerspectiveCamera(48, window.innerWidth / Math.max(window.innerHeight, 1), 0.1, 120);
        camera.position.set(0, 1.05, 10.8);
        camera.lookAt(0, 0.15, 0);
        camera.userData.basePosition = camera.position.clone();
        camera.userData.baseRotation = camera.rotation.clone();
        return camera;
    }

    function updateCamera(camera, mouse, elapsed, gsap) {
        const orbitX = Math.sin(elapsed * 0.08) * 0.34;
        const orbitY = Math.cos(elapsed * 0.07) * 0.16;
        const targetX = camera.userData.basePosition.x + orbitX + (mouse.x - 0.5) * 0.9;
        const targetY = camera.userData.basePosition.y + orbitY + (mouse.y - 0.5) * 0.45;
        const targetZ = camera.userData.basePosition.z + (mouse.y - 0.5) * 0.22;

        if (gsap) {
            gsap.to(camera.position, {
                x: targetX,
                y: targetY,
                z: targetZ,
                duration: 0.65,
                ease: "power3.out",
                overwrite: true
            });
        } else {
            camera.position.x += (targetX - camera.position.x) * 0.06;
            camera.position.y += (targetY - camera.position.y) * 0.06;
            camera.position.z += (targetZ - camera.position.z) * 0.06;
        }
        camera.lookAt((mouse.x - 0.5) * 0.45, 0.1 + (mouse.y - 0.5) * 0.22, 0);
    }

    window.GE3D = window.GE3D || {};
    window.GE3D.Camera = {
        createCamera: createCamera,
        updateCamera: updateCamera
    };
})(window);
