(function (window) {
    "use strict";

    function addGeometry(registry, geometry) {
        registry.geometries.push(geometry);
        return geometry;
    }

    function addMaterial(registry, material) {
        registry.materials.push(material);
        return material;
    }

    function createStarField(THREE, registry, isMobile) {
        const count = isMobile ? 980 : 1900;
        const positions = new Float32Array(count * 3);
        const colors = new Float32Array(count * 3);
        const sizes = new Float32Array(count);

        for (let i = 0; i < count; i++) {
            const i3 = i * 3;
            const radius = 6 + Math.random() * 22;
            const angle = Math.random() * Math.PI * 2;
            const branch = Math.sin(angle * 2.0) * 1.6;
            positions[i3] = Math.cos(angle) * radius + branch;
            positions[i3 + 1] = (Math.random() - 0.5) * 9 + Math.sin(angle * 3.0) * 0.8;
            positions[i3 + 2] = Math.sin(angle) * radius - 5 - Math.random() * 15;

            const palette = Math.random();
            colors[i3] = palette > 0.72 ? 0.66 : 0.36;
            colors[i3 + 1] = palette > 0.38 ? 0.9 : 0.5;
            colors[i3 + 2] = 1.0;
            sizes[i] = Math.random() * 0.055 + 0.018;
        }

        const geometry = addGeometry(registry, new THREE.BufferGeometry());
        geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
        geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
        geometry.setAttribute("size", new THREE.BufferAttribute(sizes, 1));

        const material = addMaterial(registry, new THREE.PointsMaterial({
            size: isMobile ? 0.055 : 0.046,
            vertexColors: true,
            transparent: true,
            opacity: 0.96,
            depthWrite: false,
            blending: THREE.AdditiveBlending
        }));

        const points = new THREE.Points(geometry, material);
        points.position.set(0, 0.4, 0);
        return points;
    }

    function createTechGrid(THREE, registry) {
        const group = new THREE.Group();
        const grid = new THREE.GridHelper(34, 48, 0x69e8ff, 0x37305d);
        grid.position.y = -3.1;
        grid.position.z = -6.2;
        grid.rotation.x = Math.PI * 0.02;
        grid.material.transparent = true;
        grid.material.opacity = 0.36;
        grid.material.depthWrite = false;
        addMaterial(registry, grid.material);
        group.add(grid);

        const laneMaterial = addMaterial(registry, new THREE.LineBasicMaterial({
            color: 0x73ffca,
            transparent: true,
            opacity: 0.34,
            blending: THREE.AdditiveBlending
        }));

        for (let i = 0; i < 7; i++) {
            const x = -6 + i * 2;
            const geometry = addGeometry(registry, new THREE.BufferGeometry().setFromPoints([
                new THREE.Vector3(x, -3.08, -20),
                new THREE.Vector3(x + (i % 2 ? 1.4 : -1.4), -3.08, 5)
            ]));
            group.add(new THREE.Line(geometry, laneMaterial));
        }

        return group;
    }

    function createLightBands(THREE, registry) {
        const group = new THREE.Group();
        const colors = [0x69e8ff, 0xa987ff, 0x73ffca, 0xff5ca8];

        colors.forEach(function (color, index) {
            const curve = new THREE.CatmullRomCurve3([
                new THREE.Vector3(-8, -1.6 + index * 0.55, -7 - index),
                new THREE.Vector3(-3.2, 0.8 + Math.sin(index), -5.6),
                new THREE.Vector3(1.6, -0.5 + index * 0.42, -6.4),
                new THREE.Vector3(7.4, 1.2 - index * 0.36, -8.6)
            ]);
            const geometry = addGeometry(registry, new THREE.TubeGeometry(curve, 96, 0.012 + index * 0.003, 6, false));
            const material = addMaterial(registry, new THREE.MeshStandardMaterial({
                color: color,
                emissive: color,
                emissiveIntensity: 2.6,
                metalness: 0.1,
                roughness: 0.25,
                transparent: true,
                opacity: 0.92
            }));
            const mesh = new THREE.Mesh(geometry, material);
            mesh.userData.offset = index * 0.8;
            group.add(mesh);
        });

        return group;
    }

    function createFloatingGeometry(THREE, registry) {
        const group = new THREE.Group();
        const geometries = [
            new THREE.TetrahedronGeometry(0.34, 1),
            new THREE.OctahedronGeometry(0.28, 1),
            new THREE.IcosahedronGeometry(0.32, 1)
        ];
        geometries.forEach(function (geometry) { addGeometry(registry, geometry); });

        for (let i = 0; i < 12; i++) {
            const material = addMaterial(registry, new THREE.MeshPhysicalMaterial({
                color: i % 3 === 0 ? 0x69e8ff : (i % 3 === 1 ? 0xa987ff : 0x73ffca),
                emissive: i % 2 ? 0x241a45 : 0x0b3e48,
                emissiveIntensity: 0.55,
                metalness: 0.58,
                roughness: 0.24,
                transmission: 0.18,
                thickness: 0.65,
                transparent: true,
                opacity: 0.72
            }));
            const mesh = new THREE.Mesh(geometries[i % geometries.length], material);
            mesh.position.set((Math.random() - 0.5) * 11, (Math.random() - 0.5) * 5.2, -1.2 - Math.random() * 8);
            mesh.rotation.set(Math.random() * Math.PI, Math.random() * Math.PI, Math.random() * Math.PI);
            mesh.userData.floatSpeed = 0.25 + Math.random() * 0.4;
            mesh.userData.floatPhase = Math.random() * Math.PI * 2;
            group.add(mesh);
        }

        return group;
    }

    window.GE3D = window.GE3D || {};
    window.GE3D.Particles = {
        createStarField: createStarField,
        createTechGrid: createTechGrid,
        createLightBands: createLightBands,
        createFloatingGeometry: createFloatingGeometry
    };
})(window);
