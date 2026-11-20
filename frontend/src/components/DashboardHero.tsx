import { useEffect, useState } from "react";
import axiosInstance from "../services/refreshTokenAxios";
import type { UserResponseDTO, MontageResponseDTO } from "../Types";
import DashboardStats from "./DashboardStats";
import PromptSuggestion from "./PromptSuggestions";
import MontageDetails from "./MontageDetails";
import ErrorBanner from "./ErrorBanner";
import { errorMessage } from "../services/errors";

export default function DashboardHero() {
  const [displayedName, setDisplayedName] = useState<string>("");
  const [mostRecentMontage, setMostRecentMontage] =
    useState<MontageResponseDTO | null>(null);
  const [mostVideosMontage, setMostVideosMontage] =
    useState<MontageResponseDTO | null>(null);
  const [highlightsError, setHighlightsError] = useState<string | null>(null);

  async function getUser() {
    try {
      const res = await axiosInstance.get(`/user`);
      const userData: UserResponseDTO = res.data;
      setDisplayedName(userData.displayedName);
    } catch {
      setDisplayedName("there"); // "Hi, there" rather than "Hi, " with nothing after it
    }
  }

  async function getMontages() {
    try {
      setHighlightsError(null);
      const longest = await axiosInstance.get("/montages/longest");
      setMostRecentMontage(longest.data || null);
      const mostVids = await axiosInstance.get("montages/most-vids");
      setMostVideosMontage(mostVids.data || null);
    } catch (err) {
      setHighlightsError(errorMessage(err, "Your montage highlights couldn't be loaded."));
    }
  }

  useEffect(() => {
    getUser();
    getMontages();
  }, []);

  return (
    <div className="w-full  poppins-font text-ink bg-page flex flex-col items-center gap-8 p-8">
      {/* Hero Section */}
      <div className=" w-[95%]">
        <h1 className="text-[40px] text-center sm:text-left sm:text-[80px] font-bold">
          Hi,&nbsp;
          <span style={{ color: "#925cfe" }}>{displayedName}</span>
        </h1>
      </div>

      {/* Main Content */}
      <div className="w-[95%]  flex flex-col sm:flex-row gap-8 bg-raised p-[15px] rounded-[15px]">
        {/* Left Side */}
        <div className="w-full sm:w-[50%]  flex flex-col ">
          <DashboardStats />
        </div>
        {/* Right Side */}
        <div className="w-full sm:w-[50%]  flex flex-col ">
          <PromptSuggestion />
        </div>
      </div>
      <h1 className="text-[30px] text-center sm:text-left w-[95%] sm:text-[70px] poppins-font font-bold">
        Montages that stand out:
      </h1>
      {highlightsError && <ErrorBanner message={highlightsError} onRetry={getMontages} />}
      <div className="flex flex-col sm:flex-row w-full justify-around">
        {/* Most Recent Montage */}
        {mostRecentMontage && (
          <div className="w-full sm:w-[40%]">
            <h2 className="text-[20px] sm:text-[35px]  my-4">Longest Time!</h2>
            <MontageDetails
              title={mostRecentMontage.name}
              topic={mostRecentMontage.prompt}
              createdAt={mostRecentMontage.createdAt}
              duration={mostRecentMontage.duration}
              photos={mostRecentMontage.videos?.map((v) => v.previewUrl) || []}
              videoSrc={mostRecentMontage.preSignedUrl}
            />
          </div>
        )}
        {/* Most Videos Montage */}
        {mostVideosMontage && (
          <div className="w-full sm:w-[40%]">
            <h2 className="text-[20px] sm:text-[35px]  my-4">Most Videos!</h2>
            <MontageDetails
              title={mostVideosMontage.name}
              topic={mostVideosMontage.prompt}
              createdAt={mostVideosMontage.createdAt}
              duration={mostVideosMontage.duration}
              photos={mostVideosMontage.videos?.map((v) => v.previewUrl) || []}
              videoSrc={mostVideosMontage.preSignedUrl}
            />
          </div>
        )}
      </div>
    </div>
  );
}
