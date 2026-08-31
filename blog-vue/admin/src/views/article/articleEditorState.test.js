import { afterEach, describe, expect, it, vi } from "vitest";
import { createArticleEditorState } from "./articleEditorState";

describe("article editor state", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("loads metadata before current Markdown content", async () => {
    const order = [];
    const api = {
      article: {
        adminById: vi.fn(async () => {
          order.push("metadata");
          return { flag: true, data: { id: 7, contentVersion: 3, articleTitle: "标题" } };
        }),
        adminContent: vi.fn(async () => {
          order.push("content");
          return { data: "# 正文", version: 3 };
        })
      }
    };
    const editor = createArticleEditorState({ api });

    await editor.load(7);

    expect(order).toEqual(["metadata", "content"]);
    expect(editor.state.metadata.articleTitle).toBe("标题");
    expect(editor.state.markdown).toBe("# 正文");
    expect(editor.state.version).toBe(3);
    expect(editor.state.status).toBe("ready");
  });

  it("debounces edits, includes expectedVersion, and coalesces edits during a save", async () => {
    vi.useFakeTimers();
    let resolveSave;
    const api = {
      article: {
        saveContent: vi.fn(() => new Promise(resolve => {
          resolveSave = resolve;
        }))
      }
    };
    const editor = createArticleEditorState({ api, debounceMs: 2000, articleId: 7, version: 4 });

    editor.setMarkdown("one");
    editor.setMarkdown("two");
    await vi.advanceTimersByTimeAsync(1999);
    expect(api.article.saveContent).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1);
    expect(api.article.saveContent).toHaveBeenCalledWith(7, {
      content: "two",
      expectedVersion: 4
    });
    expect(editor.state.status).toBe("saving");

    editor.setMarkdown("three");
    resolveSave({ flag: true, data: { version: 5 } });
    await vi.waitFor(() => expect(api.article.saveContent).toHaveBeenCalledTimes(2));

    expect(api.article.saveContent.mock.calls[1]).toEqual([7, {
      content: "three",
      expectedVersion: 5
    }]);
  });

  it("keeps the local draft and exposes a conflict after a stale write", async () => {
    const api = {
      article: {
        saveContent: vi.fn().mockRejectedValue({ response: { status: 409 } })
      }
    };
    const editor = createArticleEditorState({ api, articleId: 9, version: 2 });
    editor.setMarkdown("local draft");

    await expect(editor.saveNow()).rejects.toMatchObject({ kind: "conflict", status: 409 });

    expect(editor.state.markdown).toBe("local draft");
    expect(editor.state.dirty).toBe(true);
    expect(editor.state.status).toBe("conflict");
    expect(editor.state.saveError.kind).toBe("conflict");
  });

  it("cancels pending autosave on dispose", async () => {
    vi.useFakeTimers();
    const api = { article: { saveContent: vi.fn() } };
    const editor = createArticleEditorState({ api, articleId: 11, version: 1 });

    editor.setMarkdown("draft");
    editor.dispose();
    await vi.advanceTimersByTimeAsync(2500);

    expect(api.article.saveContent).not.toHaveBeenCalled();
  });
});
