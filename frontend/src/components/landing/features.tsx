import type { ReactNode } from "react";
import { LockArt, SearchArt, TripSliceIconArt } from "./FeatureArt";

export const FEATURES: { title: string; body: string; art: ReactNode; background: string }[] = [
  {
    title: "Finds the moments for you",
    body: "Type a topic like “the Eiffel Tower” and AI video understanding pinpoints every clip where it appears, then cuts them out.",
    art: <TripSliceIconArt />,
    background: "linear-gradient(145deg, #a477ff 0%, #6a35e8 100%)",
  },
  {
    title: "Search by memory",
    body: "Can’t remember which video it was in? Describe the scene, like “warm beach at sunset”, and search finds it.",
    art: <SearchArt />,
    background: "linear-gradient(145deg, #c35ce0 0%, #7a3df0 100%)",
  },
  {
    title: "Your footage stays yours",
    body: "Videos are only ever shown to you, through private links that expire after a few minutes.",
    art: <LockArt />,
    background: "linear-gradient(145deg, #3a2470 0%, #1b1238 100%)",
  },
];
