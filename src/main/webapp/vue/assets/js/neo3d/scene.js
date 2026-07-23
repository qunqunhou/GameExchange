(function (window) {
    "use strict";

    function createScene(THREE, registry, options) {
        const scene = new THREE.Scene();
        scene.fog = new THREE.FogExp2(0x050711, 0.025);

        const isMobile = options && options.isMobile;
        const environment = window.GE3D.Lights.createEnvironment(THREE, scene);
        registry.textures.push(environment);

        const auroraMaterial = window.GE3D.Shader.createAuroraMaterial(THREE, isMobile ? 0.86 : 1.18);
        registry.materials.push(auroraMaterial);
        const auroraGeometry = new THREE.PlaneGeometry(22, 12, 1, 1);
        registry.geometries.push(auroraGeometry);
        const aurora = new THREE.Mesh(auroraGeometry, auroraMaterial);
        aurora.position.set(0, 0.8, -11.5);
        aurora.renderOrder = -8;
        scene.add(aurora);

        const starField = window.GE3D.Particles.createStarField(THREE, registry, isMobile);
        scene.add(starField);

        const grid = window.GE3D.Particles.createTechGrid(THREE, registry);
        scene.add(grid);

        const bands = window.GE3D.Particles.createLightBands(THREE, registry);
        scene.add(bands);

        const floating = window.GE3D.Particles.createFloatingGeometry(THREE, registry);
        scene.add(floating);

        const coreMaterial = window.GE3D.Shader.createEnergyMaterial(THREE, 0x69e8ff, 0xa987ff);
        registry.materials.push(coreMaterial);

        const sphereGeometry = new THREE.SphereGeometry(isMobile ? 1.28 : 1.92, isMobile ? 36 : 64, isMobile ? 20 : 36);
        registry.geometries.push(sphereGeometry);
        const core = new THREE.Mesh(sphereGeometry, coreMaterial);
        core.position.set(isMobile ? 0.8 : 1.15, isMobile ? 1.05 : 0.72, -3.45);
        scene.add(core);

        const wireMaterial = new THREE.MeshStandardMaterial({
            color: 0x73ffca,
            emissive: 0x21f0ff,
            emissiveIntensity: 2.2,
            metalness: 0.22,
            roughness: 0.32,
            wireframe: true,
            transparent: true,
            opacity: 0.34,
            blending: THREE.AdditiveBlending
        });
        registry.materials.push(wireMaterial);
        const wireGeometry = new THREE.IcosahedronGeometry(isMobile ? 1.86 : 2.82, 3);
        registry.geometries.push(wireGeometry);
        const wireShell = new THREE.Mesh(wireGeometry, wireMaterial);
        wireShell.position.copy(core.position);
        scene.add(wireShell);

        const ringGroup = new THREE.Group();
        const ringMaterial = new THREE.MeshStandardMaterial({
            color: 0x69e8ff,
            emissive: 0x2bbfff,
            emissiveIntensity: 1.35,
            metalness: 0.45,
            roughness: 0.28,
            transparent: true,
            opacity: 0.42
        });
        registry.materials.push(ringMaterial);

        [2.72, 3.62, 4.5, 5.38].forEach(function (radius, index) {
            const geometry = new THREE.TorusGeometry(radius, 0.018 + index * 0.006, 8, 224);
            registry.geometries.push(geometry);
            const ring = new THREE.Mesh(geometry, ringMaterial);
            ring.rotation.set(0.95 + index * 0.32, 0.35 + index * 0.18, index * 0.65);
            ringGroup.add(ring);
        });
        ringGroup.position.copy(core.position);
        scene.add(ringGroup);

        const portalMaterial = new THREE.MeshStandardMaterial({
            color: 0xa987ff,
            emissive: 0x6f4dff,
            emissiveIntensity: 1.9,
            metalness: 0.28,
            roughness: 0.18,
            transparent: true,
            opacity: 0.32,
            side: THREE.DoubleSide,
            blending: THREE.AdditiveBlending,
            depthWrite: false
        });
        registry.materials.push(portalMaterial);

        const portalGroup = new THREE.Group();
        [5.8, 7.1, 8.25].forEach(function (radius, index) {
            const geometry = new THREE.TorusGeometry(radius, 0.024, 8, 260);
            registry.geometries.push(geometry);
            const portal = new THREE.Mesh(geometry, portalMaterial);
            portal.rotation.set(Math.PI * 0.5, 0.08 + index * 0.08, 0.2 + index * 0.42);
            portalGroup.add(portal);
        });
        portalGroup.position.set(1.05, 0.06, -8.6);
        scene.add(portalGroup);

        const beamMaterial = window.GE3D.Shader.createBeamMaterial(THREE);
        registry.materials.push(beamMaterial);
        const beamGeometry = new THREE.PlaneGeometry(isMobile ? 1.2 : 1.85, 14, 1, 1);
        registry.geometries.push(beamGeometry);
        const beam = new THREE.Mesh(beamGeometry, beamMaterial);
        beam.position.set(core.position.x, 0.0, -4.15);
        beam.rotation.z = -0.18;
        scene.add(beam);

        scene.userData.objects = {
            aurora: aurora,
            starField: starField,
            grid: grid,
            bands: bands,
            floating: floating,
            core: core,
            wireShell: wireShell,
            ringGroup: ringGroup,
            portalGroup: portalGroup,
            beam: beam,
            auroraMaterial: auroraMaterial,
            coreMaterial: coreMaterial,
            beamMaterial: beamMaterial
        };

        return scene;
    }

    window.GE3D = window.GE3D || {};
    window.GE3D.Scene = {
        createScene: createScene
    };
})(window);
