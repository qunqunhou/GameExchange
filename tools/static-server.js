const http = require("http");
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "../src/main/webapp");
const port = Number(process.env.PORT || 8099);
const types = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".svg": "image/svg+xml"
};

const server = http.createServer((req, res) => {
    const urlPath = decodeURIComponent(req.url.split("?")[0]);
    const route = urlPath === "/" ? "/vue/login.html" : urlPath;
    const file = path.normalize(path.join(root, route));

    if (!file.startsWith(root)) {
        res.writeHead(403);
        res.end("Forbidden");
        return;
    }

    fs.readFile(file, (error, data) => {
        if (error) {
            res.writeHead(404);
            res.end("Not found");
            return;
        }

        res.writeHead(200, {
            "Content-Type": types[path.extname(file).toLowerCase()] || "application/octet-stream"
        });
        res.end(data);
    });
});

server.listen(port, "127.0.0.1", () => {
    console.log(`GameExchange static server: http://127.0.0.1:${port}/vue/login.html`);
});
