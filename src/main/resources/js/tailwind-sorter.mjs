import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import path from 'node:path';
import readline from 'node:readline';

const sorterCache = new Map();

function resolveSorterEntrypoint(moduleBase) {
    const markerFile = path.join(moduleBase, '.trier-resolve.cjs');
    const require = createRequire(markerFile);

    return require.resolve('prettier-plugin-tailwindcss/sorter');
}

async function resolveSorter(payload) {
    const cacheKey = JSON.stringify({
        moduleBase: payload.moduleBase,
        base: payload.base,
        filepath: payload.filepath,
        configPath: payload.configPath,
        stylesheetPath: payload.stylesheetPath,
        preserveWhitespace: payload.preserveWhitespace,
        preserveDuplicates: payload.preserveDuplicates,
    });

    let sorterPromise = sorterCache.get(cacheKey);

    if (sorterPromise) return sorterPromise;

    sorterPromise = (async () => {
        const entrypoint = resolveSorterEntrypoint(payload.moduleBase);
        const { createSorter } = await import(pathToFileURL(entrypoint).href);

        return createSorter({
            base: payload.base,
            filepath: payload.filepath,
            configPath: payload.configPath,
            stylesheetPath: payload.stylesheetPath,
            preserveWhitespace: payload.preserveWhitespace,
            preserveDuplicates: payload.preserveDuplicates,
        });
    })();

    sorterCache.set(cacheKey, sorterPromise);

    try {
        return await sorterPromise;
    } catch (error) {
        sorterCache.delete(cacheKey);

        throw error;
    }
}

function serializeError(id, error) {
    const message = error instanceof Error ? error.stack || error.message : String(error);

    return JSON.stringify({ id, error: message });
}

const rl = readline.createInterface({
    input: process.stdin,
    crlfDelay: Infinity,
});

for await (const line of rl) {
    if (!line.trim()) {
        continue;
    }

    let payload;

    try {
        payload = JSON.parse(line);

        const sorter = await resolveSorter(payload);
        const values = sorter.sortClassAttributes(payload.values);

        process.stdout.write(`${JSON.stringify({id: payload.id, values})}\n`);
    } catch (error) {
        const id = payload && typeof payload.id !== 'undefined' ? payload.id : null;

        process.stdout.write(`${serializeError(id, error)}\n`);
    }
}
