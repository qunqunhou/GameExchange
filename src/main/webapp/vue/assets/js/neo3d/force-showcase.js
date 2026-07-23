(function (window, document) {
    "use strict";

    function ready(callback) {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", callback, { once: true });
        } else {
            callback();
        }
    }

    function createMouseSpark(event) {
        if (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
        const spark = document.createElement("span");
        const dx = (Math.random() - 0.5) * 34;
        const dy = (Math.random() - 0.5) * 34;
        spark.className = "ge-mouse-spark";
        spark.style.left = event.clientX + "px";
        spark.style.top = event.clientY + "px";
        spark.style.setProperty("--spark-dx", dx.toFixed(1) + "px");
        spark.style.setProperty("--spark-dy", dy.toFixed(1) + "px");
        document.body.appendChild(spark);
        spark.addEventListener("animationend", function () {
            spark.remove();
        }, { once: true });
    }

    function createRoot() {
        let root = document.querySelector(".ge-force-visual-root");
        if (!root) {
            root = document.createElement("div");
            root.className = "ge-force-visual-root";
            root.setAttribute("aria-hidden", "true");
            document.body.insertBefore(root, document.body.firstChild);
        }
        return root;
    }

    function createTransitionLayer() {
        let layer = document.querySelector(".ge-page-transition");
        if (!layer) {
            layer = document.createElement("div");
            layer.className = "ge-page-transition";
            layer.setAttribute("aria-hidden", "true");
            document.body.appendChild(layer);
        }
        return layer;
    }

    function normalizeUrl(url) {
        try {
            return new URL(url, window.location.href);
        } catch (error) {
            return null;
        }
    }

    function shouldTransitionUrl(url) {
        if (!url || url.origin !== window.location.origin || url.href === window.location.href) return false;
        if (url.protocol !== "http:" && url.protocol !== "https:") return false;
        if (url.hash && url.pathname === window.location.pathname && url.search === window.location.search) return false;
        return !/\.(?:css|js|mjs|json|map|png|jpg|jpeg|gif|svg|webp|ico|woff2?|ttf|pdf|zip)(?:$|[?#])/i.test(url.pathname);
    }

    function shouldSpaNavigate(url) {
        if (!shouldTransitionUrl(url)) return false;
        return /\/vue\/(?:login|register|dashboard|market|battle)\.html$/i.test(url.pathname);
    }

    function initPageTransition() {
        const layer = createTransitionLayer();
        const prefersReducedMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        let navigating = false;
        let lastPointer = null;
        const pageCache = new Map();

        document.addEventListener("pointerdown", function (event) {
            lastPointer = { x: event.clientX, y: event.clientY };
        }, { passive: true });

        function setTransitionOrigin(origin) {
            const x = origin && typeof origin.x === "number" ? origin.x : window.innerWidth / 2;
            const y = origin && typeof origin.y === "number" ? origin.y : window.innerHeight / 2;
            const px = x.toFixed(1) + "px";
            const py = y.toFixed(1) + "px";
            layer.style.setProperty("--transition-x", px);
            layer.style.setProperty("--transition-y", py);
        }

        function playExit(onComplete, origin) {
            setTransitionOrigin(origin);
            if (prefersReducedMotion || !window.gsap) {
                document.body.classList.add("ge-transitioning");
                window.setTimeout(onComplete, 520);
                return;
            }

            document.body.classList.add("ge-transitioning");
            window.gsap.killTweensOf(layer);
            window.gsap.timeline({
                defaults: { ease: "power3.out" },
                onComplete: onComplete
            })
                .set(layer, {
                    opacity: 0,
                    scale: 0.72,
                    clipPath: "circle(0% at var(--transition-x, 50%) var(--transition-y, 50%))",
                    filter: "saturate(1.2) brightness(1)"
                })
                .to("main[id='app'], .app-shell, .auth-shell", {
                    opacity: 0,
                    y: -24,
                    scale: 0.972,
                    filter: "blur(4px)",
                    duration: 0.32,
                    ease: "power2.in"
                }, 0)
                .to(layer, {
                    opacity: 1,
                    scale: 1,
                    clipPath: "circle(140% at var(--transition-x, 50%) var(--transition-y, 50%))",
                    filter: "saturate(1.9) brightness(1.35)",
                    duration: 0.5,
                    ease: "expo.out"
                }, 0.02)
                .to(layer, {
                    scale: 1.08,
                    filter: "saturate(2.2) brightness(1.8)",
                    duration: 0.16,
                    ease: "power2.in"
                }, 0.36)
                .to(layer, {
                    opacity: 1,
                    duration: 0.06
                }, 0.5);
        }

        function playEnter() {
            document.body.classList.remove("ge-transitioning");
            if (prefersReducedMotion || !window.gsap) {
                layer.style.opacity = "0";
                return;
            }
            window.gsap.killTweensOf(layer);
            window.gsap.fromTo(layer,
                { opacity: 1, scale: 1.04, clipPath: "circle(140% at var(--transition-x, 50%) var(--transition-y, 50%))", filter: "saturate(1.85) brightness(1.42)" },
                { opacity: 0, scale: 0.76, clipPath: "circle(0% at var(--transition-x, 50%) var(--transition-y, 50%))", filter: "saturate(1.15) brightness(1)", duration: 0.26, ease: "power3.out" }
            );
        }

        function updatePageStyle(nextDocument) {
            const nextStyle = nextDocument.head.querySelector("style");
            if (!nextStyle) return;
            let currentStyle = document.head.querySelector("style[data-ge-page-style]");
            if (!currentStyle) {
                currentStyle = document.head.querySelector("style");
                if (currentStyle) currentStyle.setAttribute("data-ge-page-style", "true");
            }
            if (currentStyle) currentStyle.textContent = nextStyle.textContent;
        }

        function isCommonStylesheet(link) {
            const href = link.getAttribute("href") || "";
            return href.indexOf("neo-ui.css") !== -1 || href.indexOf("neo3d-shared.css") !== -1;
        }

        function syncExtraStylesheets(nextDocument) {
            document.querySelectorAll('link[rel="stylesheet"]').forEach(function (link) {
                if (!isCommonStylesheet(link)) link.setAttribute("data-ge-page-extra-style", "true");
            });

            document.querySelectorAll('link[data-ge-page-extra-style="true"]').forEach(function (link) {
                link.parentNode.removeChild(link);
            });

            nextDocument.querySelectorAll('link[rel="stylesheet"]').forEach(function (link) {
                if (isCommonStylesheet(link)) return;
                const clone = document.importNode(link, true);
                clone.setAttribute("data-ge-page-extra-style", "true");
                document.head.appendChild(clone);
            });
        }

        function destroyCurrentVue() {
            if (!window.GE_CURRENT_VM || typeof window.GE_CURRENT_VM.$destroy !== "function") return;
            try {
                window.GE_CURRENT_VM.$destroy();
            } catch (error) {
                console.warn("GE page vm destroy failed", error);
            }
            window.GE_CURRENT_VM = null;
        }

        function swapBodyContent(nextDocument) {
            const visualRoot = document.querySelector(".ge-force-visual-root");
            const transitionLayer = document.querySelector(".ge-page-transition");
            const hasAuthShell = !!nextDocument.body.querySelector(".auth-shell");
            const nextClassName = (nextDocument.body.className || "").trim();

            destroyCurrentVue();
            document.body.className = [
                nextClassName,
                "ge-force-showcase-ready",
                hasAuthShell ? "ge-force-auth-page" : "ge-force-app-page"
            ].join(" ").trim();

            Array.from(document.body.childNodes).forEach(function (node) {
                if (node === visualRoot || node === transitionLayer) return;
                node.parentNode.removeChild(node);
            });

            if (visualRoot && visualRoot.parentNode === document.body) {
                document.body.insertBefore(visualRoot, document.body.firstChild);
            }

            Array.from(nextDocument.body.childNodes).forEach(function (node) {
                if (node.nodeType === 1 && node.tagName.toLowerCase() === "script") return;
                const clone = document.importNode(node, true);
                if (transitionLayer && transitionLayer.parentNode === document.body) {
                    document.body.insertBefore(clone, transitionLayer);
                } else {
                    document.body.appendChild(clone);
                }
            });

            if (transitionLayer && transitionLayer.parentNode === document.body) {
                document.body.appendChild(transitionLayer);
            }
        }

        function executePageScripts(nextDocument, url) {
            nextDocument.body.querySelectorAll("script").forEach(function (script, index) {
                if (script.src) return;
                const code = script.textContent || "";
                if (!code.trim()) return;
                try {
                    new Function(code + "\n//# sourceURL=" + url.pathname + "?spa-script=" + index)();
                } catch (error) {
                    console.error("GE page script failed", error);
                    throw error;
                }
            });
        }

        function activatePage(nextDocument, url, replace) {
            updatePageStyle(nextDocument);
            syncExtraStylesheets(nextDocument);
            swapBodyContent(nextDocument);
            document.title = nextDocument.title || document.title;
            if (replace) window.history.replaceState({ geSpa: true }, document.title, url.href);
            else window.history.pushState({ geSpa: true }, document.title, url.href);
            window.scrollTo({ top: 0, left: 0, behavior: "auto" });
            executePageScripts(nextDocument, url);
            window.GE_FORCE_UI_MOTION_READY = false;
            requestAnimationFrame(initUiMotion);
        }

        function fetchPage(url) {
            if (pageCache.has(url.href)) return Promise.resolve(pageCache.get(url.href));
            return fetch(url.href, {
                credentials: "same-origin",
                headers: { "X-Game-Exchange-SPA": "1" }
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("HTTP " + response.status);
                    return response.text();
                })
                .then(function (html) {
                    const nextDocument = new DOMParser().parseFromString(html, "text/html");
                    pageCache.set(url.href, nextDocument);
                    return nextDocument;
                });
        }

        function prefetchPage(target) {
            const url = normalizeUrl(target);
            if (!shouldSpaNavigate(url) || pageCache.has(url.href)) return;
            fetchPage(url).catch(function () {
                pageCache.delete(url.href);
            });
        }

        function go(target, options) {
            const url = normalizeUrl(target);
            if (!url || navigating) return false;
            navigating = true;
            const href = url.href;
            const replace = options && options.replace;
            const origin = options && options.origin ? options.origin : lastPointer;
            const spaNavigation = shouldSpaNavigate(url);
            const nextPage = spaNavigation ? fetchPage(url) : null;

            playExit(function () {
                if (!spaNavigation) {
                    if (replace) window.location.replace(href);
                    else window.location.href = href;
                    return;
                }

                nextPage
                    .then(function (nextDocument) {
                        activatePage(nextDocument, url, replace);
                        navigating = false;
                        playEnter();
                    })
                    .catch(function () {
                        if (replace) window.location.replace(href);
                        else window.location.href = href;
                    });
            }, origin);
            return true;
        }

        function submitForm(form) {
            if (!form || navigating) return false;
            const action = form.getAttribute("action") || window.location.href;
            const url = normalizeUrl(action);
            if (!shouldTransitionUrl(url)) return false;
            navigating = true;
            playExit(function () {
                HTMLFormElement.prototype.submit.call(form);
            });
            return true;
        }

        window.GEPageTransition = {
            go: go,
            submit: submitForm,
            playExit: playExit
        };

        document.addEventListener("click", function (event) {
            const link = event.target.closest && event.target.closest("a[href]");
            if (!link || link.target || link.hasAttribute("download") || link.dataset.noTransition === "true") return;
            if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;

            const url = normalizeUrl(link.getAttribute("href"));
            if (!shouldTransitionUrl(url)) return;

            event.preventDefault();
            go(url.href, { origin: { x: event.clientX, y: event.clientY } });
        });

        document.addEventListener("pointerover", function (event) {
            const link = event.target.closest && event.target.closest("a[href]");
            if (!link || link.target || link.hasAttribute("download") || link.dataset.noTransition === "true") return;
            prefetchPage(link.getAttribute("href"));
        }, { passive: true });

        document.addEventListener("focusin", function (event) {
            const link = event.target.closest && event.target.closest("a[href]");
            if (!link || link.target || link.hasAttribute("download") || link.dataset.noTransition === "true") return;
            prefetchPage(link.getAttribute("href"));
        });

        document.addEventListener("submit", function (event) {
            const form = event.target;
            if (!form || form.dataset.noTransition === "true" || form.target) return;
            if (!submitForm(form)) return;
            event.preventDefault();
        });

        window.addEventListener("beforeunload", function () {
            document.body.classList.add("ge-transitioning");
        });

        window.addEventListener("pageshow", function () {
            navigating = false;
            document.body.classList.remove("ge-transitioning");
            if (window.gsap) {
                window.gsap.set(layer, { opacity: 0, scaleX: 0.02, filter: "blur(0px)" });
            }
        });

        window.addEventListener("popstate", function () {
            if (navigating) return;
            const url = normalizeUrl(window.location.href);
            if (!shouldSpaNavigate(url)) {
                window.location.href = window.location.href;
                return;
            }
            navigating = true;
            fetchPage(url)
                .then(function (nextDocument) {
                    activatePage(nextDocument, url, true);
                    navigating = false;
                    playEnter();
                })
                .catch(function () {
                    window.location.reload();
                });
        });

        if (window.gsap && !prefersReducedMotion) {
            window.gsap.fromTo(layer,
                { opacity: 0.72, scale: 1.03, clipPath: "circle(140% at 50% 50%)", filter: "saturate(1.7) brightness(1.38)" },
                { opacity: 0, scale: 0.76, clipPath: "circle(0% at 50% 50%)", filter: "saturate(1.15) brightness(1)", duration: 0.22, ease: "power3.out" }
            );
        }
    }

    function createEnergyMaterial(THREE, options) {
        return new THREE.ShaderMaterial({
            transparent: true,
            depthTest: false,
            depthWrite: false,
            blending: THREE.AdditiveBlending,
            uniforms: {
                uTime: { value: 0 },
                uMouse: { value: new THREE.Vector2(0, 0) },
                uBase: { value: new THREE.Color(options.base || 0x69e8ff) },
                uAccent: { value: new THREE.Color(options.accent || 0xa987ff) },
                uHot: { value: new THREE.Color(options.hot || 0xff5ca8) },
                uOpacity: { value: options.opacity || 0.82 },
                uFresnel: { value: options.fresnel || 2.4 }
            },
            vertexShader: [
                "varying vec2 vUv;",
                "varying vec3 vNormalView;",
                "void main(){",
                "  vUv=uv;",
                "  vNormalView=normalize(normalMatrix*normal);",
                "  gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);",
                "}"
            ].join("\n"),
            fragmentShader: [
                "precision highp float;",
                "uniform float uTime;",
                "uniform vec2 uMouse;",
                "uniform vec3 uBase;",
                "uniform vec3 uAccent;",
                "uniform vec3 uHot;",
                "uniform float uOpacity;",
                "uniform float uFresnel;",
                "varying vec2 vUv;",
                "varying vec3 vNormalView;",
                "float hash(vec2 p){",
                "  p=fract(p*vec2(123.34,456.21));",
                "  p+=dot(p,p+45.32);",
                "  return fract(p.x*p.y);",
                "}",
                "float noise(vec2 p){",
                "  vec2 i=floor(p);",
                "  vec2 f=fract(p);",
                "  vec2 u=f*f*(3.0-2.0*f);",
                "  return mix(mix(hash(i),hash(i+vec2(1.0,0.0)),u.x),mix(hash(i+vec2(0.0,1.0)),hash(i+vec2(1.0,1.0)),u.x),u.y);",
                "}",
                "float fbm(vec2 p){",
                "  float v=0.0;",
                "  float a=0.5;",
                "  mat2 r=mat2(0.82,-0.57,0.57,0.82);",
                "  for(int i=0;i<5;i++){",
                "    v+=a*noise(p);",
                "    p=r*p*2.03+0.17;",
                "    a*=0.52;",
                "  }",
                "  return v;",
                "}",
                "void main(){",
                "  vec2 uv=vUv;",
                "  vec2 drift=vec2(uTime*0.035,-uTime*0.052)+uMouse*0.08;",
                "  float flow=fbm(uv*3.1+drift);",
                "  float plasma=fbm(uv*7.2+vec2(flow*1.6-uTime*0.07,uTime*0.045));",
                "  float ribbon=sin((uv.y+flow*0.42)*22.0+uTime*0.92+sin(uv.x*8.0)*1.2)*0.5+0.5;",
                "  float aurora=smoothstep(0.34,0.88,ribbon)*smoothstep(0.0,0.22,uv.y)*smoothstep(1.0,0.7,uv.y);",
                "  float veins=smoothstep(0.72,0.98,plasma);",
                "  float fresnel=pow(1.0-abs(dot(normalize(vNormalView),vec3(0.0,0.0,1.0))),uFresnel);",
                "  vec3 color=mix(uBase,uAccent,flow);",
                "  color=mix(color,uHot,aurora*0.62+veins*0.24);",
                "  color+=uBase*fresnel*1.25;",
                "  color+=uAccent*aurora*0.72;",
                "  float alpha=uOpacity*(0.22+flow*0.18+aurora*0.42+veins*0.16+fresnel*0.58);",
                "  gl_FragColor=vec4(color,clamp(alpha,0.0,1.0));",
                "}"
            ].join("\n")
        });
    }

    function createPostFX(THREE, renderer) {
        const size = new THREE.Vector2();
        renderer.getSize(size);
        const target = new THREE.WebGLRenderTarget(Math.max(1, size.x), Math.max(1, size.y), {
            depthBuffer: true,
            stencilBuffer: false
        });
        const scene = new THREE.Scene();
        const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
        const material = new THREE.ShaderMaterial({
            uniforms: {
                uScene: { value: target.texture },
                uTime: { value: 0 },
                uResolution: { value: new THREE.Vector2(Math.max(1, size.x), Math.max(1, size.y)) },
                uBloom: { value: 0.42 },
                uFocus: { value: 0.46 }
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
                "uniform sampler2D uScene;",
                "uniform float uTime;",
                "uniform vec2 uResolution;",
                "uniform float uBloom;",
                "uniform float uFocus;",
                "varying vec2 vUv;",
                "float luma(vec3 c){return dot(c,vec3(0.299,0.587,0.114));}",
                "void main(){",
                "  vec2 px=1.0/uResolution;",
                "  vec2 center=vec2(0.5);",
                "  float dist=distance(vUv,center);",
                "  float dof=smoothstep(uFocus,0.88,dist);",
                "  vec3 color=texture2D(uScene,vUv).rgb;",
                "  vec3 blur=vec3(0.0);",
                "  blur+=texture2D(uScene,vUv+px*vec2(1.5,0.0)).rgb;",
                "  blur+=texture2D(uScene,vUv+px*vec2(-1.5,0.0)).rgb;",
                "  blur+=texture2D(uScene,vUv+px*vec2(0.0,1.5)).rgb;",
                "  blur+=texture2D(uScene,vUv+px*vec2(0.0,-1.5)).rgb;",
                "  blur+=texture2D(uScene,vUv+px*vec2(2.7,2.7)).rgb;",
                "  blur+=texture2D(uScene,vUv+px*vec2(-2.7,2.7)).rgb;",
                "  blur+=texture2D(uScene,vUv+px*vec2(2.7,-2.7)).rgb;",
                "  blur+=texture2D(uScene,vUv+px*vec2(-2.7,-2.7)).rgb;",
                "  blur/=8.0;",
                "  float bright=smoothstep(0.18,0.92,luma(blur));",
                "  color=mix(color,blur,dof*0.34);",
                "  color+=blur*bright*uBloom;",
                "  color=1.0-exp(-color*1.18);",
                "  color=pow(color,vec3(0.92));",
                "  float vignette=smoothstep(0.92,0.18,dist);",
                "  float grain=fract(sin(dot(vUv*uResolution+uTime,vec2(12.9898,78.233)))*43758.5453);",
                "  color=color*(0.74+vignette*0.32)+(grain-0.5)*0.018;",
                "  gl_FragColor=vec4(color,1.0);",
                "}"
            ].join("\n")
        });
        scene.add(new THREE.Mesh(new THREE.PlaneGeometry(2, 2), material));

        return {
            target: target,
            scene: scene,
            camera: camera,
            material: material,
            resize: function () {
                renderer.getSize(size);
                const width = Math.max(1, size.x);
                const height = Math.max(1, size.y);
                target.setSize(width, height);
                material.uniforms.uResolution.value.set(width, height);
            },
            render: function (sourceScene, sourceCamera, time) {
                material.uniforms.uTime.value = time;
                renderer.setRenderTarget(target);
                renderer.render(sourceScene, sourceCamera);
                renderer.setRenderTarget(null);
                renderer.render(scene, camera);
            }
        };
    }

    function initUiMotion() {
        if (!window.gsap) return;
        const gsap = window.gsap;
        const prefersReducedMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        if (prefersReducedMotion) return;

        gsap.defaults({ ease: "power3.out" });

        gsap.set(".topbar, .brand-panel, .auth-panel, .hero-panel, .command-panel, .chart-panel, .panel, .stat-card, .arena-card, .market-stat, .live-card, .reward, .loot-card, .terminal-board, .trend-board, .rank-board", {
            transformPerspective: 1100,
            transformOrigin: "50% 50%"
        });

        const intro = gsap.timeline({ defaults: { duration: 0.86, ease: "power3.out" } });
        intro.from(".topbar, .brand-panel, .auth-panel, .hero-panel, .command-panel, .chart-panel", {
            opacity: 0,
            y: 30,
            rotateX: -3,
            stagger: 0.06,
            clearProps: "transform"
        }).from(".panel, .stat-card, .arena-card, .market-stat, .live-card, .reward, .loot-card, .terminal-board, .trend-board, .rank-board", {
            opacity: 0,
            y: 18,
            scale: 0.985,
            stagger: 0.025,
            duration: 0.68
        }, "-=0.48");

        gsap.to(".ge-force-visual-root canvas", {
            filter: "saturate(1.95) contrast(1.28) brightness(1.22)",
            duration: 2.4,
            repeat: -1,
            yoyo: true,
            ease: "sine.inOut"
        });

        document.querySelectorAll(".btn, .btn-primary, .action-btn, .combat-btn, .modal-btn").forEach(function (button) {
            if (button.dataset.geMotionBound === "true") return;
            button.dataset.geMotionBound = "true";
            button.addEventListener("mouseenter", function () {
                if (button.disabled) return;
                gsap.to(button, { y: -3, scale: 1.018, duration: 0.22, boxShadow: "0 18px 42px rgba(105,232,255,0.22)" });
            });
            button.addEventListener("mouseleave", function () {
                gsap.to(button, { y: 0, scale: 1, duration: 0.34, boxShadow: "", ease: "elastic.out(1, 0.55)" });
            });
            button.addEventListener("mousedown", function () {
                if (button.disabled) return;
                gsap.to(button, { scale: 0.975, duration: 0.08, ease: "power2.out" });
            });
            button.addEventListener("mouseup", function () {
                gsap.to(button, { scale: 1.012, duration: 0.14, ease: "power2.out" });
            });
        });

        document.querySelectorAll(".brand-panel, .auth-panel, .command-panel, .chart-panel, .panel, .arena-card, .stat-card, .market-stat").forEach(function (card) {
            if (card.dataset.geMotionBound === "true") return;
            card.dataset.geMotionBound = "true";
            card.addEventListener("mousemove", function (event) {
                const rect = card.getBoundingClientRect();
                const x = (event.clientX - rect.left) / Math.max(rect.width, 1) - 0.5;
                const y = (event.clientY - rect.top) / Math.max(rect.height, 1) - 0.5;
                card.style.setProperty("--tilt-x", (x * 20).toFixed(2) + "%");
                card.style.setProperty("--tilt-y", (y * 20).toFixed(2) + "%");
                gsap.to(card, {
                    rotateY: x * 2.4,
                    rotateX: -y * 2,
                    x: x * 4,
                    y: y * 3,
                    duration: 0.45,
                    ease: "power2.out"
                });
            });
            card.addEventListener("mouseleave", function () {
                card.style.setProperty("--tilt-x", "0%");
                card.style.setProperty("--tilt-y", "0%");
                gsap.to(card, {
                    rotateY: 0,
                    rotateX: 0,
                    x: 0,
                    y: 0,
                    duration: 0.7,
                    ease: "elastic.out(1, 0.62)"
                });
            });
        });

        const pulseTargets = ".stat-card b, .live-card b, .mini-feed b";
        if (window.GE_FORCE_UI_PULSE_READY) return;
        window.GE_FORCE_UI_PULSE_READY = true;
        setInterval(function () {
            document.querySelectorAll(pulseTargets).forEach(function (target, index) {
                gsap.fromTo(target, {
                    textShadow: "0 0 0 rgba(105,232,255,0)",
                    y: 0
                }, {
                    textShadow: "0 0 22px rgba(105,232,255,0.72)",
                    y: -1,
                    duration: 0.26,
                    yoyo: true,
                    repeat: 1,
                    delay: index * 0.025,
                    ease: "sine.inOut"
                });
            });
        }, 3200);
    }

    function initHudFeed() {
        if (window.GE_HUD_FEED_READY) return;
        window.GE_HUD_FEED_READY = true;
        setInterval(function () {
            const feed = document.querySelector(".hex-feed");
            if (!feed) return;
            const first = Math.floor(Math.random() * 65535).toString(16).toUpperCase().padStart(4, "0");
            const second = Math.floor(Math.random() * 65535).toString(16).toUpperCase().padStart(4, "0");
            const latency = Math.floor(42 + Math.random() * 74);
            feed.textContent = "0x" + first + " // 0x" + second + " // LINK " + latency + "MS";
        }, 1200);
    }

    function start() {
        if (!window.THREE || window.GE_FORCE_SHOWCASE_READY) return;
        window.GE_FORCE_SHOWCASE_READY = true;

        const THREE = window.THREE;
        const root = createRoot();
        const isAuthPage = !!document.querySelector(".auth-shell");
        document.body.classList.add(isAuthPage ? "ge-force-auth-page" : "ge-force-app-page");
        const scene = new THREE.Scene();
        const camera = new THREE.PerspectiveCamera(46, window.innerWidth / Math.max(window.innerHeight, 1), 0.1, 80);
        camera.position.set(0, 0, 8.2);

        const renderer = new THREE.WebGLRenderer({
            alpha: true,
            antialias: true,
            powerPreference: "high-performance"
        });
        renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.6));
        renderer.setSize(window.innerWidth, window.innerHeight);
        renderer.setClearColor(0x000000, 0);
        renderer.toneMapping = THREE.ACESFilmicToneMapping || THREE.ReinhardToneMapping;
        renderer.toneMappingExposure = 1.14;
        renderer.domElement.className = "ge-force-canvas";
        root.appendChild(renderer.domElement);

        function getPageLayout() {
            if (document.body.classList.contains("register-page")) {
                return { x: 0.0, y: 0.02, z: -4.78, scale: 1.18, parallaxX: 0.22, parallaxY: 0.16, bloom: 0.5, focus: 0.42 };
            }
            if (document.body.classList.contains("dashboard-page")) {
                return { x: 0.0, y: -0.1, z: -5.35, scale: 0.92, parallaxX: 0.16, parallaxY: 0.1, bloom: 0.32, focus: 0.5 };
            }
            if (document.body.classList.contains("market-page")) {
                const compact = window.innerWidth < 1100;
                return compact
                    ? { x: 0.0, y: -0.1, z: -5.35, scale: 0.88, parallaxX: 0.1, parallaxY: 0.07, bloom: 0.34, focus: 0.52 }
                    : { x: -0.1, y: -0.08, z: -5.2, scale: 0.96, parallaxX: 0.12, parallaxY: 0.08, bloom: 0.38, focus: 0.5 };
            }
            if (document.body.classList.contains("battle-page")) {
                return { x: -0.08, y: -0.18, z: -4.86, scale: 1.18, parallaxX: 0.12, parallaxY: 0.08, bloom: 0.44, focus: 0.48 };
            }
            return { x: -0.72, y: 0.02, z: -4.7, scale: 1.12, parallaxX: 0.32, parallaxY: 0.2, bloom: 0.46, focus: 0.44 };
        }

        const layout = getPageLayout();

        const group = new THREE.Group();
        group.position.set(layout.x, layout.y, layout.z);
        group.scale.setScalar(layout.scale);
        scene.add(group);

        const cyan = new THREE.MeshBasicMaterial({
            color: 0x69e8ff,
            transparent: true,
            opacity: 0.95,
            blending: THREE.AdditiveBlending,
            depthTest: false,
            depthWrite: false
        });
        const violet = new THREE.MeshBasicMaterial({
            color: 0xa987ff,
            transparent: true,
            opacity: 0.82,
            blending: THREE.AdditiveBlending,
            depthTest: false,
            depthWrite: false
        });
        const pink = new THREE.MeshBasicMaterial({
            color: 0xff5ca8,
            transparent: true,
            opacity: 0.74,
            blending: THREE.AdditiveBlending,
            depthTest: false,
            depthWrite: false
        });
        const mintWire = new THREE.MeshBasicMaterial({
            color: 0x73ffca,
            wireframe: true,
            transparent: true,
            opacity: 0.78,
            blending: THREE.AdditiveBlending,
            depthTest: false,
            depthWrite: false
        });

        const shell = new THREE.Mesh(new THREE.IcosahedronGeometry(1.45, 3), mintWire);
        group.add(shell);

        const coreMaterial = createEnergyMaterial(THREE, {
            base: 0x69e8ff,
            accent: 0xa987ff,
            hot: 0xff5ca8,
            opacity: 0.86,
            fresnel: 2.1
        });
        const core = new THREE.Mesh(new THREE.SphereGeometry(0.72, 64, 36), coreMaterial);
        core.renderOrder = 16;
        group.add(core);

        const auraMaterial = createEnergyMaterial(THREE, {
            base: 0x73ffca,
            accent: 0x69e8ff,
            hot: 0xff5ca8,
            opacity: 0.34,
            fresnel: 1.35
        });
        const aura = new THREE.Mesh(new THREE.SphereGeometry(1.18, 64, 36), auraMaterial);
        aura.renderOrder = 15;
        group.add(aura);

        [
            { radius: 2.0, material: cyan, x: 1.15, y: 0.18, z: 0.08 },
            { radius: 2.75, material: violet, x: 1.35, y: -0.18, z: 0.72 },
            { radius: 3.5, material: pink, x: 1.52, y: 0.06, z: 1.16 },
            { radius: 4.25, material: cyan, x: 1.68, y: 0.26, z: 1.64 }
        ].forEach(function (item, index) {
            const ring = new THREE.Mesh(new THREE.TorusGeometry(item.radius, 0.026 + index * 0.004, 10, 220), item.material);
            ring.rotation.set(item.x, item.y, item.z);
            ring.renderOrder = 20 + index;
            group.add(ring);
        });

        const beamMaterial = new THREE.ShaderMaterial({
            transparent: true,
            depthTest: false,
            depthWrite: false,
            blending: THREE.AdditiveBlending,
            uniforms: {
                uTime: { value: 0 }
            },
            vertexShader: [
                "varying vec2 vUv;",
                "void main(){",
                "  vUv=uv;",
                "  gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);",
                "}"
            ].join("\n"),
            fragmentShader: [
                "precision highp float;",
                "uniform float uTime;",
                "varying vec2 vUv;",
                "void main(){",
                "  float center=1.0-smoothstep(0.0,0.48,abs(vUv.x-0.5));",
                "  float pulse=sin((vUv.y-uTime*0.16)*32.0)*0.5+0.5;",
                "  vec3 color=mix(vec3(0.38,0.91,1.0),vec3(1.0,0.36,0.78),vUv.y);",
                "  gl_FragColor=vec4(color,center*(0.22+pulse*0.34));",
                "}"
            ].join("\n")
        });
        const beam = new THREE.Mesh(new THREE.PlaneGeometry(1.0, 7.8), beamMaterial);
        beam.position.set(0, 0, -0.3);
        beam.rotation.z = -0.22;
        beam.renderOrder = 30;
        group.add(beam);

        let seed = 24681357;
        function seededRandom() {
            seed = (seed * 1664525 + 1013904223) >>> 0;
            return seed / 4294967296;
        }

        const count = window.innerWidth < 720 ? 620 : 1400;
        const positions = new Float32Array(count * 3);
        const colors = new Float32Array(count * 3);
        for (let i = 0; i < count; i++) {
            const i3 = i * 3;
            positions[i3] = (seededRandom() - 0.5) * 18;
            positions[i3 + 1] = (seededRandom() - 0.5) * 9;
            positions[i3 + 2] = -seededRandom() * 16;
            colors[i3] = seededRandom() > 0.5 ? 0.42 : 0.9;
            colors[i3 + 1] = seededRandom() > 0.5 ? 0.92 : 0.48;
            colors[i3 + 2] = 1.0;
        }
        const starsGeometry = new THREE.BufferGeometry();
        starsGeometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
        starsGeometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
        const stars = new THREE.Points(starsGeometry, new THREE.PointsMaterial({
            size: 0.052,
            vertexColors: true,
            transparent: true,
            opacity: 0.9,
            blending: THREE.AdditiveBlending,
            depthWrite: false,
            depthTest: false
        }));
        scene.add(stars);

        const postFX = createPostFX(THREE, renderer);
        postFX.material.uniforms.uBloom.value = window.innerWidth < 720 ? 0.24 : layout.bloom;
        postFX.material.uniforms.uFocus.value = layout.focus;
        let mouseX = 0;
        let mouseY = 0;
        let smoothMouseX = 0;
        let smoothMouseY = 0;
        let lastSparkAt = 0;
        window.addEventListener("mousemove", function (event) {
            mouseX = event.clientX / Math.max(window.innerWidth, 1) - 0.5;
            mouseY = event.clientY / Math.max(window.innerHeight, 1) - 0.5;
            document.documentElement.style.setProperty("--ge-parallax-x", mouseX.toFixed(4));
            document.documentElement.style.setProperty("--ge-parallax-y", mouseY.toFixed(4));
            document.documentElement.style.setProperty("--ge-parallax-shadow-x", (mouseX * 12).toFixed(2) + "px");
            document.documentElement.style.setProperty("--ge-parallax-shadow-y", (mouseY * 8).toFixed(2) + "px");
            if (performance.now() - lastSparkAt > 42) {
                lastSparkAt = performance.now();
                createMouseSpark(event);
            }
        }, { passive: true });

        window.addEventListener("resize", function () {
            camera.aspect = window.innerWidth / Math.max(window.innerHeight, 1);
            camera.updateProjectionMatrix();
            renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.6));
            renderer.setSize(window.innerWidth, window.innerHeight);
            postFX.resize();
            postFX.material.uniforms.uBloom.value = window.innerWidth < 720 ? 0.24 : layout.bloom;
        }, { passive: true });

        window.GE_FORCE_DEBUG = {
            renderer: renderer,
            scene: scene,
            camera: camera,
            postFX: postFX,
            group: group
        };
        document.body.classList.add("ge-force-showcase-ready");
        if (window.gsap) {
            window.gsap.fromTo(group.scale,
                { x: layout.scale * 0.72, y: layout.scale * 0.72, z: layout.scale * 0.72 },
                { x: layout.scale, y: layout.scale, z: layout.scale, duration: 1.05, ease: "expo.out" }
            );
        }

        function animate(time) {
            const t = time * 0.001;
            const pageLayout = getPageLayout();
            layout.x += (pageLayout.x - layout.x) * 0.045;
            layout.y += (pageLayout.y - layout.y) * 0.045;
            layout.z += (pageLayout.z - layout.z) * 0.045;
            layout.scale += (pageLayout.scale - layout.scale) * 0.045;
            layout.parallaxX += (pageLayout.parallaxX - layout.parallaxX) * 0.045;
            layout.parallaxY += (pageLayout.parallaxY - layout.parallaxY) * 0.045;
            if (postFX && postFX.material && postFX.material.uniforms) {
                postFX.material.uniforms.uBloom.value += ((window.innerWidth < 720 ? 0.24 : pageLayout.bloom) - postFX.material.uniforms.uBloom.value) * 0.045;
                postFX.material.uniforms.uFocus.value += (pageLayout.focus - postFX.material.uniforms.uFocus.value) * 0.045;
            }
            smoothMouseX += (mouseX - smoothMouseX) * 0.065;
            smoothMouseY += (mouseY - smoothMouseY) * 0.065;
            coreMaterial.uniforms.uTime.value = t;
            auraMaterial.uniforms.uTime.value = t * 0.82 + 8.0;
            coreMaterial.uniforms.uMouse.value.set(smoothMouseX, smoothMouseY);
            auraMaterial.uniforms.uMouse.value.set(-smoothMouseX * 0.6, -smoothMouseY * 0.6);
            beamMaterial.uniforms.uTime.value = t;
            group.position.x = layout.x + smoothMouseX * layout.parallaxX;
            group.position.y = layout.y - smoothMouseY * layout.parallaxY;
            group.position.z = layout.z;
            group.scale.setScalar(layout.scale);
            group.rotation.y = smoothMouseX * 0.18;
            group.rotation.x = -smoothMouseY * 0.1;
            camera.position.x = smoothMouseX * 0.32;
            camera.position.y = -smoothMouseY * 0.16;
            camera.lookAt(layout.x * 0.16 + smoothMouseX * 0.18, layout.y * 0.12 - smoothMouseY * 0.12, layout.z);
            shell.rotation.x = t * 0.13;
            shell.rotation.y = -t * 0.19;
            core.scale.setScalar(1 + Math.sin(t * 1.6) * 0.035);
            aura.scale.setScalar(1 + Math.sin(t * 1.2 + 0.8) * 0.045);
            group.children.forEach(function (child, index) {
                if (child.geometry && child.geometry.type === "TorusGeometry") {
                    child.rotation.z += 0.0038 + index * 0.00065 + Math.abs(smoothMouseX) * 0.0009;
                    child.rotation.x += smoothMouseY * 0.00045;
                }
            });
            stars.rotation.y = t * 0.018 + smoothMouseX * 0.08;
            postFX.render(scene, camera, t);
            requestAnimationFrame(animate);
        }

        requestAnimationFrame(animate);
    }

    ready(function () {
        initPageTransition();
        start();
        initHudFeed();
        requestAnimationFrame(function () {
            setTimeout(initUiMotion, 80);
        });
    });
})(window, document);
