import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
//import type { MontageResponseDTO } from "../Types";
import axiosInstance from "../services/refreshTokenAxios";
import { errorMessage } from "../services/errors";
import ErrorBanner from "./ErrorBanner";

export default function DashboardStats() {
  const navigate = useNavigate();
  const [videoCount, setVideoCount] = useState(0);
  const [montageCount, setMontageCount] = useState(0);
  const [loading, setLoading] = useState(true);

  const [statsError, setStatsError] = useState<string | null>(null);

  const fetchStats = async () => {
    try {
      setStatsError(null);
      const videosRes = await axiosInstance.get("/videos");
      setVideoCount(videosRes.data.length);
      const montagesRes = await axiosInstance.get("/montages");
      setMontageCount(montagesRes.data.length);
    } catch (err) {
      setStatsError(errorMessage(err, "Your stats couldn't be loaded."));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
  }, []);

  if (loading) {
    return <div className="text-ink">Loading...</div>;
  }

  return (
    <div className="">
      {/* Stats Section: an error rather than zeros that look real */}
      {statsError ? (
        <ErrorBanner message={statsError} onRetry={fetchStats} className="!w-full mb-12" />
      ) : (
      <div className="grid grid-cols-2 gap-6 mb-12">
        <div className="bg-gradient-to-br from-sunken to-sunken p-6 rounded-lg">
          <p className="text-ink-soft text-sm mb-2">Total Videos</p>
          <p className="text-4xl font-thin">{videoCount}</p>
        </div>
        <div className="bg-gradient-to-br from-sunken to-sunken p-6 rounded-lg">
          <p className="text-ink-soft text-sm mb-2">Total Montages</p>
          <p className="text-4xl font-thin">{montageCount}</p>
        </div>
      </div>
      )}

      {/* Quick Action Buttons */}
      <div className="flex flex-col gap-4 ">
        <h1 className=" text-[30px] font-normal">Ready to Create?</h1>
        <button
          onClick={() => navigate("/app/create")}
          className="w-[100%] bg-[#925cfe] hover:bg-[#7a4dd1] text-white text-[30px] font-bold h-[80px]  rounded-lg transition"
        >
          Create Montage!
        </button>
      </div>

      {/* Recent Montages Section */}
      {/* <div>
        <h2 className="text-2xl font-bold mb-6">Recent Montages</h2>
        {recentMontages.length === 0 ? (
          <p className="text-muted">No montages created yet. Start by creating one!</p>
        ) : (
          <div className="space-y-4">
            {recentMontages.map((montage) => (
              <div
                key={montage.id}
                className="bg-gray-900 p-4 rounded-lg hover:bg-sunken transition cursor-pointer"
                onClick={() => navigate("/app/montages")}
              >
                <h3 className="font-bold text-lg">{montage.name}</h3>
                <p className="text-muted text-sm">Created: {new Date(montage.createdAt).toLocaleDateString()}</p>
              </div>
            ))}
          </div>
        )}
      </div> */}
    </div>
  );
}
