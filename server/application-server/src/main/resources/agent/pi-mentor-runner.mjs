// JSONL stdin/stdout echo skeleton — validates the Hephaestus transport layer only (no Pi SDK).
//
// Frame protocol (one JSON object per line, terminated by \n):
//   {"type":"ping"}                   → {"type":"pong"}
//   {"type":"echo","payload":<any>}   → {"type":"echo_back","payload":<any>}
//   {"type":"emit","count":<int>,...} → emits `count` `{"type":"tick","n":i,...}` frames
//   any other / malformed line → logged to stderr, no response
//
// `readline` with `crlfDelay: Infinity` splits on \n / \r\n / \r only — U+2028/U+2029 are 3-byte
// UTF-8 sequences containing no \n/\r and survive framing intact. Don't substitute a Scanner or
// generic Unicode line iterator: those would mis-split.

import { createInterface } from "node:readline";

const out = process.stdout;
const err = process.stderr;

function writeFrame(obj) {
    out.write(JSON.stringify(obj));
    out.write("\n");
}

const rl = createInterface({ input: process.stdin, crlfDelay: Infinity });

rl.on("line", (line) => {
    if (!line) return;
    let frame;
    try {
        frame = JSON.parse(line);
    } catch (e) {
        err.write(`[pi-mentor-runner] parse error: ${e.message}\n`);
        return;
    }
    switch (frame?.type) {
        case "ping":
            writeFrame({ type: "pong" });
            break;
        case "echo":
            writeFrame({ type: "echo_back", payload: frame.payload });
            break;
        case "emit": {
            // Strict: protocol calls for a positive integer; reject floats / NaN / negatives.
            if (!Number.isInteger(frame.count) || frame.count <= 0) {
                err.write(`[pi-mentor-runner] emit requires positive integer count\n`);
                break;
            }
            const count = frame.count;
            const tag = typeof frame.tag === "string" ? frame.tag : "burst";
            for (let i = 0; i < count; i++) {
                writeFrame({ type: "tick", n: i, tag });
            }
            break;
        }
        case "exit": {
            const code = Number.isInteger(frame.code) ? frame.code : 0;
            // Allow the buffered stdout to drain before exit
            out.write("", () => process.exit(code));
            break;
        }
        default:
            err.write(`[pi-mentor-runner] unknown frame type: ${frame?.type}\n`);
    }
});

rl.on("close", () => {
    process.exit(0);
});

// Announce ourselves so adapters can time spawn → first-frame deterministically.
writeFrame({ type: "ready" });
