/**
 * Small D1-compatible façade over Node's built-in SQLite.
 * The live Worker continues to use Cloudflare D1 unmodified.
 *
 * Only the D1 methods exercised by Kovelyx routes are implemented. Do not
 * expose this object to Better Auth: use DatabaseSync directly for AUTH_DB.
 */
function sqliteParameter(value) {
  if (value instanceof ArrayBuffer) return Buffer.from(value);
  if (ArrayBuffer.isView(value) && !(value instanceof Uint8Array))
    return Buffer.from(value.buffer, value.byteOffset, value.byteLength);
  return value;
}
export function createLocalD1(database) {
  function statement(sql) {
    const prepared = database.prepare(sql);
    function bound(params = []) {
      const args = params.map(sqliteParameter);
      const run = () => {
        const result = prepared.run(...args);
        return {success: true, meta: {
          changes: Number(result.changes),
          last_row_id: Number(result.lastInsertRowid)
        }};
      };
      return {
        bind(...next) { return bound(next); },
        async first(column) {
          const row = prepared.get(...args) ?? null;
          return column === undefined ? row : (row?.[column] ?? null);
        },
        async all() { return {success: true, results: prepared.all(...args)}; },
        async run() { return run(); },
        _run: run
      };
    }
    return bound();
  }
  return {
    prepare: statement,
    async batch(statements) {
      if (!Array.isArray(statements) || statements.some(s => typeof s?._run !== "function"))
        throw new TypeError("Invalid SQLite batch");
      // Synchronous transaction: all CAS checks and media chunks stay atomic.
      database.exec("BEGIN IMMEDIATE");
      try {
        const results = statements.map(s => s._run());
        database.exec("COMMIT");
        return results;
      } catch (error) {
        database.exec("ROLLBACK");
        throw error;
      }
    }
  };
}
