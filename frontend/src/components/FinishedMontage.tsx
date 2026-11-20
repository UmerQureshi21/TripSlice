import { useEffect, useState } from "react";

const MAX_RELOADS = 10; // about 20 seconds

interface Props {
  preSignedUrl: string;
  progressMessage: string;
}

export default function FinishedMontage({
  preSignedUrl,
  progressMessage,
}: Props) {
  const [reloadKey, setReloadKey] = useState(0);
  const [videoReady, setVideoReady] = useState(false);
  const gaveUp = reloadKey >= MAX_RELOADS;

  // The file can take a moment to appear in S3, so the video reloads until it plays, up to a limit
  useEffect(() => {
    if (!preSignedUrl || videoReady || gaveUp) return;
    const interval = setInterval(() => {
      setReloadKey((k) => k + 1);
    }, 2000);

    return () => clearInterval(interval);
  }, [preSignedUrl, videoReady, gaveUp]);

  return (
    <>
      <div className="relative  w-full bg-page flex items-center justify-center ">
        <h1 className="w-[70%] text-ink text-[40px] font-bold text-center ">
          {progressMessage}
        </h1>
      </div>
      <div className="w-full bg-page py-[20px] flex flex-col items-center text-ink">
          <video
            key={`${preSignedUrl}-${reloadKey}`} // changing key forces React to reload
            src={preSignedUrl}
            className="w-[90%] max-w-[400px] rounded-[40px] border-[2px] border-white"
            controls
            autoPlay
            onPlay={() => setVideoReady(true)}
            onError={() => setVideoReady(false)}
          />
          {gaveUp && !videoReady && (
            <p role="alert" className="mt-[16px] w-[90%] max-w-[400px] text-center text-muted">
              The preview is taking longer than usual to load. Your montage is saved, so you can
              watch it from your Montage Library.
            </p>
          )}
      </div>
    </>
  );
}
