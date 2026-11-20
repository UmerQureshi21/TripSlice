import { useEffect, useState } from "react";
import type { MontageResponseDTO } from "../Types";
import MontageDetails from "./MontageDetails";
import { deleteMontage, getAllMontages } from "../services/montageService";
import { errorMessage } from "../services/errors";
import ErrorBanner from "./ErrorBanner";

export default function Montages() {
  const [montages, setMontages] = useState<MontageResponseDTO[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState<boolean>(false);

  async function getMontages() {
    try {
      setMontages(await getAllMontages());
      setLoadError(null);
    } catch (err) {
      setLoadError(errorMessage(err, "Your montages couldn't be loaded."));
    } finally {
      setLoaded(true);
    }
  }

  async function handleDelete(id: number): Promise<string | null> {
    const error = await deleteMontage(id);
    if (!error) await getMontages();
    return error;
  }

  useEffect(() => {
    getMontages();
  }, []);

  return (
    <div className="w-full bg-page flex justify-center pb-[100px]">
      {loadError ? (
        <ErrorBanner message={loadError} onRetry={getMontages} className="mt-[40px]" />
      ) : !loaded ? null : montages.length > 0 ? (
        <div className="bg-surface w-[95%] mb-[100px] max-h-[700px] overflow-y-scroll rounded-[10px] grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 p-4">
          {montages?.map((montage, index) => (
            <MontageDetails
              key={`montage ${index}`}
              title={montage.name}
              topic={montage.prompt}
              createdAt={montage.createdAt}
              duration={montage.duration}
              photos={montage.videos.map((video) => video.previewUrl)}
              videoSrc={montage.preSignedUrl}
              searchable={montage.searchable}
              onDelete={montage.id !== undefined ? () => handleDelete(montage.id!) : undefined}
            />
          ))}
        </div>
      ) : (
        <div className="text-ink min-h-screen mt-[100px] poppins-font sm:text-[40px] text-[30px] w-[95%] sm:w-[50%] text-center">
          No montages have been made yet. Go on the{" "}
          <span className="text-[#925cfe]">Create Montage</span> page to relive
          your first vacation!
        </div>
      )}
    </div>
  );
}
