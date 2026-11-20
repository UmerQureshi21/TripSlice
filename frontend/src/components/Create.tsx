import { useEffect, useRef, useState } from "react";
import type { VideoRequestDTO, MontageResponseDTO } from "../Types";
import SelectableFileDetails from "./VideoDetailsSelectable";
import MontageProgressWebSocket from "./MontageProcessWebSocket";
import FinishedMontage from "./FinishedMontage";
import { getAllVideos } from "../services/videoService";
import { createMontage } from "../services/montageService";
import { errorMessage as messageFor, isOffline } from "../services/errors";
import ErrorBanner from "./ErrorBanner";
import { MAX_TITLE_LENGTH, MAX_TOPIC_LENGTH, MAX_VIDEOS_PER_MONTAGE } from "../limits";

export default function UploadCopy() {
  const [montageSubmitted, isMontageSubmitted] = useState<boolean>(false);
  const [montageFinished, isMontageFinished] = useState<boolean>(false);
  const [prevFiles, setPrevFiles] = useState<VideoRequestDTO[]>([]);
  const [clicks, setClicks] = useState<boolean[]>([]);
  const [sentence, setSentence] = useState<string>("");
  const [title, setTitle] = useState<string>("");
  const [preSignedUrl, setPreSignedUrl] = useState<string>("");
  const [montageName, setMontageName] = useState<string>("");
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [showingError, setShowingError] = useState<boolean>(false);
  const [selectionWarning, setSelectionWarning] = useState<string>("");
  const selectedCount = clicks.filter(Boolean).length;
  const redirectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Clear the pending redirect if the page is left
  useEffect(() => {
    return () => {
      if (redirectTimer.current) clearTimeout(redirectTimer.current);
    };
  }, []);

  // Show the error on the progress screen, then go back to the form (the error stays visible there)
  function failMontage(message: string) {
    setErrorMessage(message);
    setShowingError(true);
    redirectTimer.current = setTimeout(() => {
      setShowingError(false);
      isMontageSubmitted(false);
    }, 3000);
  }

  const [loadError, setLoadError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState<boolean>(false);

  async function loadVideos() {
    try {
      setPrevFiles(await getAllVideos());
      setLoadError(null);
    } catch (err) {
      setLoadError(messageFor(err, "Your videos couldn't be loaded."));
    } finally {
      setLoaded(true);
    }
  }

  async function handleSubmit() {
    try {
      setErrorMessage("");
      isMontageSubmitted(true);
      const montageData: MontageResponseDTO = await createMontage(
        prevFiles,
        clicks,
        title,
        sentence
      );
      if (montageData) {
        setPreSignedUrl(montageData.preSignedUrl);
        setMontageName(montageData.name);
        isMontageFinished(true);
      } else {
        failMontage("Something went wrong while creating the montage.");
      }
    } catch (err) {
      // The connection can drop while the server is still working, so the montage may yet appear
      failMontage(
        isOffline(err)
          ? "We lost the connection while your montage was being made. It may still finish, so check your Montage Library in a minute before trying again."
          : messageFor(err, "Something went wrong while creating the montage. Please try again.")
      );
    }
  }

  const handleToggleSelect = (index: number) => {
    const newClicks = [...clicks];
    if (!newClicks[index] && selectedCount >= MAX_VIDEOS_PER_MONTAGE) {
      setSelectionWarning(
        `A montage can use at most ${MAX_VIDEOS_PER_MONTAGE} videos. Unselect one to pick a different video.`
      );
      return;
    }
    setSelectionWarning("");
    newClicks[index] = !newClicks[index];
    setClicks(newClicks);
  };

  // Nothing is selected at first, clicks fills in as videos are toggled
  useEffect(() => {
    loadVideos();
  }, []);

  return (
    <div className="w-full bg-page flex flex-col items-center">
      {montageSubmitted && montageFinished ? (
        <>
          <FinishedMontage
            preSignedUrl={preSignedUrl}
            progressMessage={`${montageName} has been created!`}
          />
          <button
            className="hover:cursor-pointer mb-[100px] mt-[15px] hover:shadow-[0_0_10px_white] shadow-[0_0_0_white] transition duration-150 ease relative w-[80%] max-w-[400px] bg-[#925CFE] px-[30px] py-[15px] rounded-[20px] poppins-font text-white text-[20px]"
            onClick={() => {
              isMontageFinished(false);
              isMontageSubmitted(false);
            }}
          >
            Create Another Montage!
          </button>
        </>
      ) : montageSubmitted && showingError ? (
        <div className="w-full bg-page min-h-[700px] py-16 flex flex-col items-center justify-center gap-6 poppins-font text-center">
          <div className="w-12 h-12 rounded-full bg-red-500 flex items-center justify-center text-white text-[28px] font-bold">
            !
          </div>
          <p className="text-red-400 text-[22px] w-[80%] max-w-[500px]">
            {errorMessage}
          </p>
          <p className="text-muted text-sm">
            Taking you back to the create page...
          </p>
        </div>
      ) : montageSubmitted ? (
        <div className="w-full bg-page pb-[50px]">
          <MontageProgressWebSocket />
        </div>
      ) : (
        <div className="w-full bg-page flex flex-col items-center gap-[50px]">
          {errorMessage !== "" && (
            <div role="alert" className="w-[90%] md:w-[65%] rounded-[20px] border border-red-500 bg-red-500/10 px-[20px] py-[15px] poppins-font text-red-400 text-center">
              {errorMessage}
            </div>
          )}
          {/* Desktop Layout */}
          <div className="hidden md:flex text-ink bg-surface pt-[50px] poppins w-[65%] rounded-[20px] flex-col items-center justify-center pb-[50px]">
            <div className="flex flex-col items-center w-full gap-[20px]">
              <div className="flex w-[60%] justify-between items-center gap-[20px]">
                <h1 className="text-[35px] text-[#925cfe] whitespace-nowrap">
                  Topic:
                </h1>
                <input
                  type="text"
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                    setSentence(e.target.value);
                  }}
                  value={sentence}
                  maxLength={MAX_TOPIC_LENGTH}
                  aria-label="Topic"
                  autoFocus
                  className="flex-1 outline-none border-b-[1px] border-b-[#925cfe] pb-[3px] bg-transparent"
                  style={{ caretColor: "#925cfe" }}
                />
              </div>
              <div className="flex w-[60%] justify-between items-center gap-[20px]">
                <h1 className="text-[35px] text-[#925cfe] whitespace-nowrap">
                  Title:
                </h1>
                <input
                  type="text"
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                    setTitle(e.target.value);
                  }}
                  value={title}
                  maxLength={MAX_TITLE_LENGTH}
                  aria-label="Title"
                  autoFocus
                  className="flex-1 outline-none border-b-[1px] border-b-[#925cfe] pb-[3px] bg-transparent"
                  style={{ caretColor: "#925cfe" }}
                />
              </div>
            </div>
            <button
              className={`mt-[25px] px-[30px] py-[15px] rounded-[20px] poppins-font text-white text-[20px] font-medium transition duration-150 ease w-[65%] ${
                sentence === "" ||
                title === "" ||
                !clicks.some((selected) => selected)
                  ? "hover:cursor-not-allowed light:text-faint"
                  : "hover:cursor-pointer hover:shadow-[0_0_10px_white] shadow-[0_0_0_white]"
              }`}
              onClick={handleSubmit}
              disabled={
                sentence === "" ||
                title === "" ||
                !clicks.some((selected) => selected)
              }
              style={{
                backgroundColor:
                  sentence === "" ||
                  title === "" ||
                  !clicks.some((selected) => selected)
                    ? "var(--color-disabled)"
                    : "#925CFE",
              }}
            >
              Generate Montage!
            </button>
          </div>

          {/* Mobile Layout */}
          <div className="flex md:hidden text-ink bg-surface pt-[30px] poppins w-[90%] rounded-[20px] flex-col items-center justify-center pb-[30px] gap-[20px]">
            <div className="flex flex-col w-full items-center gap-[20px] px-[20px]">
              <div className="flex flex-col w-full gap-[8px]">
                <h1 className="text-[24px] text-[#925cfe]">Topic:</h1>
                <input
                  type="text"
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                    setSentence(e.target.value);
                  }}
                  value={sentence}
                  maxLength={MAX_TOPIC_LENGTH}
                  aria-label="Topic"
                  autoFocus
                  className="outline-none border-b-[1px] border-b-[#925cfe] pb-[8px] bg-transparent text-ink"
                  style={{ caretColor: "#925cfe" }}
                />
              </div>
              <div className="flex flex-col w-full gap-[8px]">
                <h1 className="text-[24px] text-[#925cfe]">Title:</h1>
                <input
                  type="text"
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                    setTitle(e.target.value);
                  }}
                  value={title}
                  maxLength={MAX_TITLE_LENGTH}
                  aria-label="Title"
                  autoFocus
                  className="outline-none border-b-[1px] border-b-[#925cfe] pb-[8px] bg-transparent text-ink"
                  style={{ caretColor: "#925cfe" }}
                />
              </div>
            </div>
            <button
              className={`w-[80%] px-[30px] py-[15px] rounded-[20px] poppins-font text-white text-[18px] font-medium transition duration-150 ease ${
                sentence === "" ||
                title === "" ||
                !clicks.some((selected) => selected)
                  ? "hover:cursor-not-allowed light:text-faint"
                  : "hover:cursor-pointer hover:shadow-[0_0_10px_white] shadow-[0_0_0_white]"
              }`}
              onClick={handleSubmit}
              disabled={
                sentence === "" ||
                title === "" ||
                !clicks.some((selected) => selected)
              }
              style={{
                backgroundColor:
                  sentence === "" ||
                  title === "" ||
                  !clicks.some((selected) => selected)
                    ? "var(--color-disabled)"
                    : "#925CFE",
              }}
            >
              Generate Montage!
            </button>
          </div>
          <div className="w-[95%] flex flex-col items-center gap-[8px] poppins-font text-center">
            <p className="text-ink-soft">
              Selected{" "}
              <span className={selectedCount >= MAX_VIDEOS_PER_MONTAGE ? "text-amber-300 font-bold" : "text-[#925cfe] font-bold"}>
                {selectedCount} of {MAX_VIDEOS_PER_MONTAGE}
              </span>{" "}
              videos · topic {sentence.length}/{MAX_TOPIC_LENGTH} · title {title.length}/{MAX_TITLE_LENGTH} characters
            </p>
            {selectionWarning !== "" && (
              <p role="alert" className="rounded-[20px] border border-amber-500 bg-amber-500/10 px-[20px] py-[10px] text-amber-300">
                {selectionWarning}
              </p>
            )}
          </div>
          {loadError ? (
            <ErrorBanner message={loadError} onRetry={loadVideos} className="mb-[100px]" />
          ) : !loaded ? null : prevFiles.length > 0 ? (
            <div className="bg-surface w-[95%] mb-[100px] max-h-[700px] overflow-y-scroll rounded-[10px] grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 p-4">
              {prevFiles.map((file, index) => (
                <div
                  className="flex items-center justify-center"
                  key={`Prev Video ${index}`}
                >
                  <SelectableFileDetails
                    name={file.name}
                    size={0}
                    type={""}
                    videoSrc={file.previewUrl}
                    isSelected={clicks[index] || false}
                    onToggle={() => handleToggleSelect(index)}
                  />
                </div>
              ))}
            </div>
          ) : (
            <div className="text-ink min-h-screen mt-[100px] poppins-font sm:text-[40px] text-[30px] w-[75%] sm:w-[50%] text-center">
              No videos have been uploaded yet. Go on the{" "}
              <span className="text-[#925cfe]">Video Library</span> page to
              upload videos that you can use later!
            </div>
          )}
        </div>
      )}
    </div>
  );
}
