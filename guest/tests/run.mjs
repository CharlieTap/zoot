import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { WASI } from 'node:wasi';

const output = process.argv[2];
const saves = mkdtempSync(join(tmpdir(), 'zoot-save-test-'));

function instantiate(name) {
    const wasi = new WASI({ version: 'preview1', preopens: { '/saves': saves } });
    const module = new WebAssembly.Module(readFileSync(join(output, `${name}.wasm`)));
    const guest = new WebAssembly.Instance(module, { wasi_snapshot_preview1: wasi.wasiImport });
    wasi.initialize(guest);
    return guest.exports;
}

try {
    instantiate('language').test_language();
    assert.equal(instantiate('save-upgrade').test_upgrade(), 0, 'legacy save upgrade');

    const writer = instantiate('save-io');
    const size = writer.size();
    const expected = Uint8Array.from({ length: size }, (_, index) => index * 31);
    new Uint8Array(writer.memory.buffer, writer.buffer(), size).set(expected);
    assert.equal(writer.read_save(0, size), -1, 'missing save');
    assert.equal(writer.write_save(0, size), size, 'write save');
    assert.deepEqual(new Uint8Array(readFileSync(join(saves, 'save-0.bin'))), expected);

    // A fresh instance must reopen the same file, not reuse the previous guest's memory.
    const reader = instantiate('save-io');
    assert.equal(reader.read_save(0, size - 1), -1, 'insufficient destination capacity');
    assert.equal(reader.read_save(0, size), size, 'reopen save');
    assert.deepEqual(new Uint8Array(reader.memory.buffer, reader.buffer(), size), expected);
    assert.equal(reader.write_save(0, 16), 16, 'truncate existing save');
    assert.equal(readFileSync(join(saves, 'save-0.bin')).length, 16);
    assert.equal(reader.read_save(0, size), 16, 'short read');
    assert.equal(reader.valid(1), 0, 'unused slot');
    console.log('Guest cache, renderer, shader, background, language and save tests passed');
} finally {
    rmSync(saves, { recursive: true });
}
