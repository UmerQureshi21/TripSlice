import { useState } from "react";

/**
 * Two-step delete: "Delete" asks to confirm, then runs `onDelete`. `onDelete` resolves to an
 * error message when it fails, which is shown right here.
 */
export default function DeleteControl({
  label,
  onDelete,
}: {
  label: string; // what is being deleted, e.g. "video"
  onDelete: () => Promise<string | null>;
}) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setBusy(true);
    setError(null);
    const message = await onDelete();
    setBusy(false);
    if (message) {
      setError(message);
      setConfirming(false);
    }
  }

  // Clicks here mustn't reach the card underneath (which may select or play the video)
  return (
    <div className="flex flex-col items-end gap-[4px]" onClick={(e) => e.stopPropagation()}>
      {confirming ? (
        <div className="flex items-center gap-[8px] text-xs">
          <span className="text-muted">Delete this {label}?</span>
          <button
            type="button"
            disabled={busy}
            onClick={confirm}
            className="rounded-full bg-red-500 px-[10px] py-[3px] font-medium text-white hover:bg-red-600 disabled:opacity-60 cursor-pointer"
          >
            {busy ? "Deleting..." : "Yes, delete"}
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => setConfirming(false)}
            className="rounded-full px-[10px] py-[3px] text-muted hover:text-ink cursor-pointer"
          >
            Cancel
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setConfirming(true)}
          aria-label={`Delete ${label}`}
          className="rounded-full px-[10px] py-[3px] text-xs text-muted hover:text-red-500 hover:bg-red-500/10 transition-colors cursor-pointer"
        >
          Delete
        </button>
      )}
      {error && (
        <p role="alert" className="text-xs text-red-500 text-right max-w-[260px]">
          {error}
        </p>
      )}
    </div>
  );
}
