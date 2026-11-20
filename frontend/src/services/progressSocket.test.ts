import { describe, expect, it, vi } from "vitest";

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- the fake client's config is untyped on purpose
type FakeClient = Record<string, any>;
const created: FakeClient[] = [];
vi.mock("@stomp/stompjs", () => ({
  Client: class {
    connectHeaders: Record<string, string> = {};
    subscribe = vi.fn();
    constructor(config: FakeClient) {
      Object.assign(this, config);
      created.push(this as unknown as FakeClient);
    }
  },
}));
vi.mock("./refreshTokenAxios", () => ({ default: { get: vi.fn() } }));

import axiosInstance from "./refreshTokenAxios";
import { createProgressClient } from "./progressSocket";
import { tokenManager } from "./tokenManager";

describe("createProgressClient", () => {
  it("refreshes the token, connects with it and subscribes to the user's queue", async () => {
    const onMessage = vi.fn();
    createProgressClient("montage-progress", onMessage);
    const client = created[0];
    expect(client.brokerURL).toMatch(/^ws.*\/gs-guide-websocket$/);

    tokenManager.setToken("Bearer t");
    await client.beforeConnect();
    expect(axiosInstance.get).toHaveBeenCalledWith("/user");
    expect(client.connectHeaders).toEqual({ Authorization: "Bearer t" });

    client.onConnect();
    const [destination, handler] = client.subscribe.mock.calls[0];
    expect(destination).toBe("/user/queue/montage-progress");
    handler({ body: '{"content":"hi","montagePath":null}' });
    expect(onMessage).toHaveBeenCalledWith({ content: "hi", montagePath: null });
  });

  it("still connects if the refresh fails, and logs socket errors", async () => {
    vi.mocked(axiosInstance.get).mockRejectedValueOnce(new Error("offline"));
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    const log = vi.spyOn(console, "log").mockImplementation(() => {});
    createProgressClient("upload-progress", vi.fn());
    const client = created[created.length - 1];
    tokenManager.clearToken();
    await client.beforeConnect();
    expect(client.connectHeaders).toEqual({ Authorization: "" });

    client.debug("frame");
    client.onWebSocketError(new Event("error"));
    client.onStompError({ headers: { message: "bad" }, body: "details" });
    expect(error).toHaveBeenCalledTimes(4);
    expect(log).toHaveBeenCalledWith("frame"); // tests run in dev mode
  });
});
