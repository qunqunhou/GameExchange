(function (window) {
    "use strict";

    function createLights(THREE, scene) {
        const group = new THREE.Group();

        const ambient = new THREE.AmbientLight(0x8fcfff, 0.45);
        const directional = new THREE.DirectionalLight(0xbfd7ff, 1.35);
        directional.position.set(-4, 6, 7);

        const cyan = new THREE.PointLight(0x69e8ff, 2.2, 18, 1.8);
        cyan.position.set(-3.6, 1.8, 3.8);

        const violet = new THREE.PointLight(0xa987ff, 2.4, 18, 1.7);
        violet.position.set(4.2, 0.6, 2.6);

        const mint = new THREE.PointLight(0x73ffca, 1.35, 14, 1.6);
        mint.position.set(0, -2.4, 3.2);

        group.add(ambient, directional, cyan, violet, mint);
        scene.add(group);

        return {
            group: group,
            cyan: cyan,
            violet: violet,
            mint: mint,
            update: function (elapsed, mouse) {
                cyan.position.x = -3.6 + Math.sin(elapsed * 0.42) * 0.7 + (mouse.x - 0.5) * 0.8;
                cyan.position.y = 1.8 + Math.cos(elapsed * 0.36) * 0.35;
                violet.position.x = 4.2 + Math.cos(elapsed * 0.32) * 0.65;
                violet.position.y = 0.6 + (mouse.y - 0.5) * 0.7;
                mint.intensity = 1.15 + Math.sin(elapsed * 0.72) * 0.18;
            }
        };
    }

    function createEnvironment(THREE, scene) {
        const canvas = document.createElement("canvas");
        canvas.width = 16;
        canvas.height = 256;
        const ctx = canvas.getContext("2d");
        const gradient = ctx.createLinearGradient(0, 0, 0, canvas.height);
        gradient.addColorStop(0, "#a987ff");
        gradient.addColorStop(0.45, "#07101f");
        gradient.addColorStop(1, "#69e8ff");
        ctx.fillStyle = gradient;
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        const texture = new THREE.CanvasTexture(canvas);
        texture.mapping = THREE.EquirectangularReflectionMapping;
        scene.environment = texture;
        scene.userData.environmentTexture = texture;
        return texture;
    }

    window.GE3D = window.GE3D || {};
    window.GE3D.Lights = {
        createLights: createLights,
        createEnvironment: createEnvironment
    };
})(window);
