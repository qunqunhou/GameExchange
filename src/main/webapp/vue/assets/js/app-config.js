(function (window) {
    "use strict";

    const VUE_PATH_MARKER = "/vue/";

    function normalizeBasePath(basePath) {
        if (!basePath || basePath === "/") return "";
        const pathWithLeadingSlash = basePath.startsWith("/")
            ? basePath
            : "/" + basePath;
        return pathWithLeadingSlash.replace(/\/+$/, "");
    }

    function detectBasePath(pathname) {
        const markerIndex = pathname.indexOf(VUE_PATH_MARKER);
        return markerIndex >= 0 ? pathname.slice(0, markerIndex) : "";
    }

    function normalizeApiPath(path) {
        if (typeof path !== "string") {
            throw new TypeError("API path must be a string");
        }
        if (!path) return "";
        return path.startsWith("/") ? path : "/" + path;
    }

    // 可在加载本脚本前显式设置 BASE_PATH；默认根据当前 /vue/ 页面地址推导。
    const configuredBasePath = typeof window.BASE_PATH === "string"
        ? window.BASE_PATH
        : detectBasePath(window.location.pathname);

    window.BASE_PATH = normalizeBasePath(configuredBasePath);
    window.GE_API = function (path) {
        return window.BASE_PATH + normalizeApiPath(path);
    };
})(window);
