(function (window, document) {
    "use strict";

    const HEARTBEAT_INTERVAL_MS = 20000;
    let timerId = null;
    let lifecycleId = 0;
    let activeRequest = null;
    let blockedByUnauthorized = false;

    function hasStoredPlayer() {
        try {
            return Boolean(window.localStorage.getItem("playerId"));
        } catch (error) {
            return false;
        }
    }

    function clearTimer() {
        if (timerId === null) return;
        window.clearInterval(timerId);
        timerId = null;
    }

    function stop(options) {
        lifecycleId++;
        clearTimer();
        activeRequest = null;
        blockedByUnauthorized = Boolean(options && options.unauthorized);
    }

    function sendNow(expectedLifecycleId) {
        const requestLifecycleId = expectedLifecycleId === undefined
            ? lifecycleId
            : expectedLifecycleId;

        if (requestLifecycleId !== lifecycleId || blockedByUnauthorized) {
            return Promise.resolve(false);
        }
        if (!hasStoredPlayer()) {
            stop();
            return Promise.resolve(false);
        }
        if (activeRequest && activeRequest.lifecycleId === requestLifecycleId) {
            return activeRequest.promise;
        }

        const request = {
            lifecycleId: requestLifecycleId,
            promise: null
        };
        request.promise = window.fetch(window.GE_API("/presence/heartbeat"), {
            method: "POST",
            credentials: "same-origin",
            cache: "no-store",
            keepalive: true,
            headers: {
                "Accept": "application/json"
            }
        })
            .then(function (response) {
                if (response.status === 401 && lifecycleId === requestLifecycleId) {
                    stop({ unauthorized: true });
                    return false;
                }
                return response.ok;
            })
            .catch(function () {
                return false;
            })
            .finally(function () {
                if (activeRequest === request) activeRequest = null;
            });
        activeRequest = request;
        return request.promise;
    }

    function start() {
        lifecycleId++;
        blockedByUnauthorized = false;
        clearTimer();

        if (!hasStoredPlayer()) return Promise.resolve(false);

        const startedLifecycleId = lifecycleId;
        timerId = window.setInterval(function () {
            sendNow(startedLifecycleId);
        }, HEARTBEAT_INTERVAL_MS);
        return sendNow(startedLifecycleId);
    }

    function handleVisibilityChange() {
        if (document.visibilityState !== "visible" || timerId === null) return;
        sendNow();
    }

    function autoStart() {
        if (hasStoredPlayer()) start();
    }

    document.addEventListener("visibilitychange", handleVisibilityChange);
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", autoStart, { once: true });
    } else {
        autoStart();
    }

    window.GEPresenceHeartbeat = {
        start: start,
        stop: stop,
        sendNow: sendNow,
        isRunning: function () {
            return timerId !== null && !blockedByUnauthorized;
        }
    };
})(window, document);
