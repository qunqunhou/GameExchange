(function (window) {
    "use strict";

    function createPostFX(THREE, renderer, scene, camera) {
        const size = new THREE.Vector2(window.innerWidth, window.innerHeight);
        const pixelRatio = Math.min(window.devicePixelRatio || 1, window.innerWidth < 720 ? 1.25 : 1.65);
        const target = new THREE.WebGLRenderTarget(size.x * pixelRatio, size.y * pixelRatio, {
            depthBuffer: true,
            stencilBuffer: false,
            samples: 0
        });
        const postScene = new THREE.Scene();
        const postCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
        const material = new THREE.ShaderMaterial({
            uniforms: {
                tDiffuse: { value: target.texture },
                uResolution: { value: new THREE.Vector2(size.x * pixelRatio, size.y * pixelRatio) },
                uTime: { value: 0 },
                uBloom: { value: window.innerWidth < 720 ? 0.42 : 0.58 },
                uFilm: { value: 0.13 },
                uFocus: { value: window.innerWidth >= 900 ? 0.26 : 0.08 }
            },
            vertexShader: [
                "varying vec2 vUv;",
                "void main(){",
                "  vUv=uv;",
                "  gl_Position=vec4(position.xy,0.0,1.0);",
                "}"
            ].join("\n"),
            fragmentShader: [
                "precision highp float;",
                "uniform sampler2D tDiffuse;",
                "uniform vec2 uResolution;",
                "uniform float uTime;",
                "uniform float uBloom;",
                "uniform float uFilm;",
                "uniform float uFocus;",
                "varying vec2 vUv;",
                "vec3 sampleBlur(vec2 uv, vec2 dir){",
                "  vec3 c=texture2D(tDiffuse,uv).rgb*0.28;",
                "  c+=texture2D(tDiffuse,uv+dir*1.4).rgb*0.18;",
                "  c+=texture2D(tDiffuse,uv-dir*1.4).rgb*0.18;",
                "  c+=texture2D(tDiffuse,uv+dir*3.1).rgb*0.11;",
                "  c+=texture2D(tDiffuse,uv-dir*3.1).rgb*0.11;",
                "  c+=texture2D(tDiffuse,uv+dir.yx*2.2).rgb*0.07;",
                "  c+=texture2D(tDiffuse,uv-dir.yx*2.2).rgb*0.07;",
                "  return c;",
                "}",
                "float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453123);}",
                "void main(){",
                "  vec2 px=1.0/uResolution;",
                "  vec2 uv=vUv;",
                "  vec3 base=texture2D(tDiffuse,uv).rgb;",
                "  vec3 blur=sampleBlur(uv,px);",
                "  float l=max(max(blur.r,blur.g),blur.b);",
                "  vec3 bloom=blur*smoothstep(0.18,0.92,l)*uBloom;",
                "  float vignette=smoothstep(0.92,0.24,distance(uv,vec2(0.5)));",
                "  float grain=(hash(uv*uResolution+uTime)-0.5)*uFilm;",
                "  float focus=distance(uv,vec2(0.5,0.48));",
                "  vec3 dof=mix(base,blur,smoothstep(0.22,0.86,focus)*uFocus);",
                "  vec3 color=(dof+bloom)*mix(0.68,1.0,vignette)+grain;",
                "  color=pow(max(color,0.0),vec3(0.94));",
                "  gl_FragColor=vec4(color,1.0);",
                "}"
            ].join("\n")
        });

        const quad = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), material);
        postScene.add(quad);

        return {
            render: function (elapsed) {
                material.uniforms.uTime.value = elapsed || 0;
                renderer.setRenderTarget(target);
                renderer.render(scene, camera);
                renderer.setRenderTarget(null);
                renderer.render(postScene, postCamera);
            },
            setSize: function (width, height) {
                const ratio = Math.min(window.devicePixelRatio || 1, width < 720 ? 1.25 : 1.65);
                target.setSize(width * ratio, height * ratio);
                material.uniforms.uResolution.value.set(width * ratio, height * ratio);
                material.uniforms.uBloom.value = width < 720 ? 0.42 : 0.58;
                material.uniforms.uFocus.value = width >= 900 ? 0.26 : 0.08;
            },
            dispose: function () {
                target.dispose();
                material.dispose();
                quad.geometry.dispose();
            }
        };
    }

    window.GE3D = window.GE3D || {};
    window.GE3D.PostFX = {
        createPostFX: createPostFX
    };
})(window);
