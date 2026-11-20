import { useEffect, useState } from "react";
import FileDetails from "./VideoDetails";
import UploadProgressWebSocket from "./UploadProgressWebSocket";
import type { VideoRequestDTO } from "../Types";
import {
  clearVideoCache,
  deleteVideo,
  getAllVideos,
  uploadVideos,
} from "../services/videoService";
import { errorMessage } from "../services/errors";
import ErrorBanner from "./ErrorBanner";
import { UPLOAD_RULES, validateUpload } from "../limits";

export default function Upload() {
  const [prevFiles, setPrevFiles] = useState<VideoRequestDTO[]>([]);
  const [selectionErrors, setSelectionErrors] = useState<string[]>([]);
  const [checking, setChecking] = useState<boolean>(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [totalVids, setTotalVids] = useState<number>(0);
  const [uploading, setUploading] = useState<boolean>(false);
  const [uploadFileNames, setUploadFileNames] = useState<string[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState<boolean>(false);

  async function loadVideos() {
    try {
      const videos = await getAllVideos();
      setPrevFiles(videos);
      setTotalVids(videos.length);
      setLoadError(null);
    } catch (err) {
      setLoadError(errorMessage(err, "Your videos couldn't be loaded."));
    } finally {
      setLoaded(true);
    }
  }

  async function handleDelete(id: number): Promise<string | null> {
    const error = await deleteVideo(id);
    if (!error) await loadVideos();
    return error;
  }

  useEffect(() => {
    loadVideos();
  }, []);

  async function handleFileInput(e: React.ChangeEvent<HTMLInputElement>) {
    const input = e.target;
    if (!input.files || input.files.length === 0) return;
    const files = input.files;
    const list = Array.from(files);
    // Lets the same file be picked again after fixing a problem
    input.value = "";

    setUploadError(null);
    setChecking(true);
    const problems = await validateUpload(list, totalVids);
    setChecking(false);
    setSelectionErrors(problems);
    if (problems.length > 0) return;

    setUploadFileNames(list.map((f) => f.name));
    setUploading(true);
    const error = await uploadVideos(files);
    setUploading(false);
    setUploadError(error);
    // Even a partly failed upload may have saved some videos
    clearVideoCache();
    await loadVideos();
  }

  return (
    <div className="w-full ">
      <div className="relative w-full min-h-[100px] bg-page prompt-section flex flex-col items-center">
        <div className="w-full pt-[40px] bg-page flex flex-col items-center">
          <button
            disabled={uploading || checking}
            className="hover:cursor-pointer mt-[-20px] hover:shadow-[0_0_10px_white] shadow-[0_0_0_white] transition duration-350 ease relative w-[60%] bg-[#925CFE] px-[30px] py-[15px] rounded-[20px] poppins-font text-white text-[20px]"
          >
            <input
              type="file"
              multiple // Always makes the request a FileList even though you put in one file
              accept="video/*"
              onChange={handleFileInput}
              disabled={uploading || checking}
              aria-label="Upload videos"
              className="absolute w-full h-full bg-red-300 left-0 top-0 rounded-[20px] opacity-0 hover:cursor-pointer z-[100]"
            />
            {checking ? "Checking videos..." : uploading ? "Uploading..." : "Upload Videos"}
          </button>
          <p className="pt-[12px] w-[90%] text-muted text-sm text-center">
            {UPLOAD_RULES} You have {totalVids} of 15.
          </p>
          <h1 className="py-[15px] w-[90%] text-ink font-normal sm:w-full text-center">
            Before you can use parts of your video for a montage, you must
            upload it first, for proper analyzing capabilities
          </h1>
          {selectionErrors.length > 0 ? (
            <div role="alert" className="mb-[15px] w-[90%] md:w-[65%] rounded-[20px] border border-amber-500 bg-amber-500/10 px-[20px] py-[15px] text-amber-300">
              <p className="font-bold mb-[6px]">Nothing was uploaded. Please fix these first:</p>
              <ul className="list-disc pl-[20px] text-left">
                {selectionErrors.map((message) => (
                  <li key={message}>{message}</li>
                ))}
              </ul>
            </div>
          ) : null}
          {uploadError ? (
            <div role="alert" className="mb-[15px] w-[90%] md:w-[65%] rounded-[20px] border border-red-500 bg-red-500/10 px-[20px] py-[15px] text-red-400 text-center">
              {uploadError}
            </div>
          ) : null}
        </div>
        <div className="text-ink poppins w-[90%] flex flex-col items-center justify-center">
          <div className="flex flex-col items-center w-full "></div>
        </div>
      </div>
      {uploading && (
        <UploadProgressWebSocket fileNames={uploadFileNames} />
      )}
      <div>
        <div className="w-full   bg-page  flex flex-col items-center  ">
          {loadError ? (
            <ErrorBanner message={loadError} onRetry={loadVideos} className="mt-[40px] mb-[100px]" />
          ) : !loaded ? null : prevFiles.length > 0 ? (
            <div className="bg-surface w-[95%] mb-[100px] max-h-[700px] overflow-y-scroll rounded-[10px] grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 p-4">
              {prevFiles.map((file, index) => (
                <div
                  className="flex items-center justify-center hover:cursor-pointer"
                  key={`Prev Video ${index}`}
                >
                  <FileDetails
                    name={file.name}
                    size={0}
                    type={""}
                    videoSrc={file.previewUrl}
                    searchable={file.searchable}
                    onDelete={file.id !== undefined ? () => handleDelete(file.id!) : undefined}
                  />
                </div>
              ))}
            </div>
          ) : (
            <div className=" text-ink min-h-screen mt-[100px] poppins-font w-[75%] text-[25px] sm:text-[40px] sm:w-[50%] text-center">
              No videos have been uploaded yet. Click on the{" "}
              <span className="text-[#925cfe]"> Upload</span> button to upload
              videos that you can use later!
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
