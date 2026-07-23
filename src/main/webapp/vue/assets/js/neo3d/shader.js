(function (window) {
    "use strict";

    const vertexShader = [
        "varying vec2 vUv;",
        "varying vec3 vPosition;",
        "void main() {",
        "  vUv = uv;",
        "  vPosition = position;",
        "  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);",
        "}"
    ].join("\n");

    const auroraFragmentShader = [
        "precision highp float;",
        "uniform float uTime;",
        "uniform vec2 uMouse;",
        "uniform float uIntensity;",
        "varying vec2 vUv;",
        "float mod289(float x){return x-floor(x*(1.0/289.0))*289.0;}",
        "vec4 mod289(vec4 x){return x-floor(x*(1.0/289.0))*289.0;}",
        "vec4 perm(vec4 x){return mod289(((x*34.0)+1.0)*x);}",
        "float snoise(vec3 p){",
        "  const vec2 C=vec2(1.0/6.0,1.0/3.0);",
        "  const vec4 D=vec4(0.0,0.5,1.0,2.0);",
        "  vec3 i=floor(p+dot(p,C.yyy));",
        "  vec3 x0=p-i+dot(i,C.xxx);",
        "  vec3 g=step(x0.yzx,x0.xyz);",
        "  vec3 l=1.0-g;",
        "  vec3 i1=min(g.xyz,l.zxy);",
        "  vec3 i2=max(g.xyz,l.zxy);",
        "  vec3 x1=x0-i1+C.xxx;",
        "  vec3 x2=x0-i2+C.yyy;",
        "  vec3 x3=x0-D.yyy;",
        "  i=mod289(i);",
        "  vec4 p0=perm(perm(perm(i.z+vec4(0.0,i1.z,i2.z,1.0))+i.y+vec4(0.0,i1.y,i2.y,1.0))+i.x+vec4(0.0,i1.x,i2.x,1.0));",
        "  float n_=0.142857142857;",
        "  vec3 ns=n_*D.wyz-D.xzx;",
        "  vec4 j=p0-49.0*floor(p0*ns.z*ns.z);",
        "  vec4 x_=floor(j*ns.z);",
        "  vec4 y_=floor(j-7.0*x_);",
        "  vec4 x=x_*ns.x+ns.yyyy;",
        "  vec4 y=y_*ns.x+ns.yyyy;",
        "  vec4 h=1.0-abs(x)-abs(y);",
        "  vec4 b0=vec4(x.xy,y.xy);",
        "  vec4 b1=vec4(x.zw,y.zw);",
        "  vec4 s0=floor(b0)*2.0+1.0;",
        "  vec4 s1=floor(b1)*2.0+1.0;",
        "  vec4 sh=-step(h,vec4(0.0));",
        "  vec4 a0=b0.xzyw+s0.xzyw*sh.xxyy;",
        "  vec4 a1=b1.xzyw+s1.xzyw*sh.zzww;",
        "  vec3 p1=vec3(a0.xy,h.x);",
        "  vec3 p2=vec3(a0.zw,h.y);",
        "  vec3 p3=vec3(a1.xy,h.z);",
        "  vec3 p4=vec3(a1.zw,h.w);",
        "  vec4 norm=1.79284291400159-0.85373472095314*vec4(dot(p1,p1),dot(p2,p2),dot(p3,p3),dot(p4,p4));",
        "  p1*=norm.x; p2*=norm.y; p3*=norm.z; p4*=norm.w;",
        "  vec4 m=max(0.6-vec4(dot(x0,x0),dot(x1,x1),dot(x2,x2),dot(x3,x3)),0.0);",
        "  m=m*m;",
        "  return 42.0*dot(m*m,vec4(dot(p1,x0),dot(p2,x1),dot(p3,x2),dot(p4,x3)));",
        "}",
        "float fbm(vec3 p){",
        "  float v=0.0;",
        "  float a=0.5;",
        "  for(int i=0;i<5;i++){",
        "    v+=a*snoise(p);",
        "    p*=2.02;",
        "    a*=0.52;",
        "  }",
        "  return v;",
        "}",
        "void main(){",
        "  vec2 uv=vUv;",
        "  vec2 p=(uv-0.5)*vec2(1.8,1.0);",
        "  float t=uTime*0.08;",
        "  float flow=fbm(vec3(p*2.1, t));",
        "  float wave=sin((p.x+flow*0.55+uTime*0.075)*7.5);",
        "  float veil=smoothstep(0.08,0.96,wave*0.5+0.5)*smoothstep(0.72,0.0,abs(p.y+0.18+flow*0.2));",
        "  float plasma=smoothstep(0.35,0.95,fbm(vec3(p*4.0+uMouse*0.35, t*1.7)));",
        "  float horizon=smoothstep(0.82,0.1,abs(p.y+0.02));",
        "  vec3 cyan=vec3(0.32,0.88,1.0);",
        "  vec3 violet=vec3(0.58,0.42,1.0);",
        "  vec3 mint=vec3(0.37,1.0,0.76);",
        "  vec3 rose=vec3(1.0,0.25,0.62);",
        "  vec3 color=mix(cyan,violet,uv.x)*veil + mint*plasma*0.22 + rose*horizon*0.05;",
        "  float alpha=clamp(veil*0.36+plasma*0.12+horizon*0.05,0.0,0.56)*uIntensity;",
        "  gl_FragColor=vec4(color,alpha);",
        "}"
    ].join("\n");

    const energyVertexShader = [
        "uniform float uTime;",
        "varying vec2 vUv;",
        "varying vec3 vNormal;",
        "void main(){",
        "  vUv=uv;",
        "  vNormal=normalMatrix*normal;",
        "  vec3 p=position + normal * sin((position.y + uTime * 0.45) * 4.0) * 0.025;",
        "  gl_Position=projectionMatrix*modelViewMatrix*vec4(p,1.0);",
        "}"
    ].join("\n");

    const energyFragmentShader = [
        "precision highp float;",
        "uniform float uTime;",
        "uniform vec3 uColorA;",
        "uniform vec3 uColorB;",
        "varying vec2 vUv;",
        "varying vec3 vNormal;",
        "void main(){",
        "  float fresnel=pow(1.0-abs(dot(normalize(vNormal),vec3(0.0,0.0,1.0))),2.6);",
        "  float scan=sin((vUv.y*18.0)+uTime*1.4)*0.5+0.5;",
        "  float ring=smoothstep(0.42,0.95,scan);",
        "  vec3 color=mix(uColorA,uColorB,vUv.y+ring*0.18);",
        "  float alpha=0.24+fresnel*0.56+ring*0.12;",
        "  gl_FragColor=vec4(color,alpha);",
        "}"
    ].join("\n");

    function createAuroraMaterial(THREE, intensity) {
        return new THREE.ShaderMaterial({
            transparent: true,
            depthWrite: false,
            blending: THREE.AdditiveBlending,
            uniforms: {
                uTime: { value: 0 },
                uMouse: { value: new THREE.Vector2(0.5, 0.5) },
                uIntensity: { value: intensity || 1 }
            },
            vertexShader: vertexShader,
            fragmentShader: auroraFragmentShader
        });
    }

    function createEnergyMaterial(THREE, colorA, colorB) {
        return new THREE.ShaderMaterial({
            transparent: true,
            depthWrite: false,
            blending: THREE.AdditiveBlending,
            uniforms: {
                uTime: { value: 0 },
                uColorA: { value: new THREE.Color(colorA || 0x69e8ff) },
                uColorB: { value: new THREE.Color(colorB || 0xa987ff) }
            },
            vertexShader: energyVertexShader,
            fragmentShader: energyFragmentShader
        });
    }

    function createBeamMaterial(THREE) {
        return new THREE.ShaderMaterial({
            transparent: true,
            depthWrite: false,
            side: THREE.DoubleSide,
            blending: THREE.AdditiveBlending,
            uniforms: {
                uTime: { value: 0 },
                uColorA: { value: new THREE.Color(0x69e8ff) },
                uColorB: { value: new THREE.Color(0xff5ca8) }
            },
            vertexShader: vertexShader,
            fragmentShader: [
                "precision highp float;",
                "uniform float uTime;",
                "uniform vec3 uColorA;",
                "uniform vec3 uColorB;",
                "varying vec2 vUv;",
                "float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453123);}",
                "float noise(vec2 p){",
                "  vec2 i=floor(p); vec2 f=fract(p); vec2 u=f*f*(3.0-2.0*f);",
                "  return mix(mix(hash(i),hash(i+vec2(1.0,0.0)),u.x),mix(hash(i+vec2(0.0,1.0)),hash(i+vec2(1.0,1.0)),u.x),u.y);",
                "}",
                "void main(){",
                "  vec2 uv=vUv;",
                "  float center=1.0-smoothstep(0.0,0.48,abs(uv.x-0.5));",
                "  float flow=noise(vec2(uv.y*3.0-uTime*0.18, uv.x*7.0));",
                "  float scan=sin((uv.y+flow*0.22-uTime*0.12)*34.0)*0.5+0.5;",
                "  vec3 color=mix(uColorA,uColorB,uv.y+flow*0.2);",
                "  float alpha=center*(0.16+scan*0.28+flow*0.22);",
                "  gl_FragColor=vec4(color,alpha);",
                "}"
            ].join("\n")
        });
    }

    window.GE3D = window.GE3D || {};
    window.GE3D.Shader = {
        createAuroraMaterial: createAuroraMaterial,
        createEnergyMaterial: createEnergyMaterial,
        createBeamMaterial: createBeamMaterial
    };
})(window);
