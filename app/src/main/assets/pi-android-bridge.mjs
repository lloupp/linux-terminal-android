// Android tool bridge for @earendil-works/pi-coding-agent 1.0.4.
// Uses the documented RPC extension UI round trip; no second network server.
import { Type } from "@earendil-works/pi-ai";
export default function (pi) {
  const string = Type.String();
  const specs = [
    ["read", "Read a UTF-8 text file relative to the selected workspace (max 1 MiB).", { path: string }, ["path"], p => ["file", { operation: "read", path: p.path }]],
    ["write", "Write a UTF-8 text file after explicit Android approval.", { path: string, content: string }, ["path", "content"], p => ["file", { operation: "write", path: p.path, text: p.content }]],
    ["edit", "Replace text that matches exactly once, after approval.", { path: string, oldText: string, newText: string }, ["path", "oldText", "newText"], p => ["edit", p]],
    ["bash", "Execute a shell command in the selected workspace, after approval (30 seconds).", { command: string }, ["command"], p => ["shell", p]],
    ["workspace", "List the selected private workspace.", {}, [], () => ["workspace", {}]],
    ["clipboard", "Read/write Android clipboard only with explicit approval.", { operation: Type.Union([Type.Literal("read"), Type.Literal("write")]), text: string }, ["operation"], p => ["clipboard", p]],
    ["share", "Open Android share sheet for text, after approval.", { text: string }, ["text"], p => ["share", p]],
  ];
  for (const [name, description, properties, required, map] of specs) {
    pi.registerTool({ name, label: name, description,
      parameters: Type.Object(Object.fromEntries(Object.entries(properties).map(([key, schema]) =>
        [key, required.includes(key) ? schema : Type.Optional(schema)])), { additionalProperties: false }),
      async execute(_id, parameters, signal, _update, ctx) {
        if (signal?.aborted) throw new Error("Cancelled");
        const [tool, args] = map(parameters);
        const value = await ctx.ui.input("Android tools", JSON.stringify({ tool, arguments: args }), { timeout: 125000 });
        if (signal?.aborted || value === undefined) throw new Error("Cancelled or approval expired");
        const result = JSON.parse(value);
        if (!result.success) throw new Error(result.text);
        return { content: [{ type: "text", text: result.text }], details: { android: true } };
      },
    });
  }
}
