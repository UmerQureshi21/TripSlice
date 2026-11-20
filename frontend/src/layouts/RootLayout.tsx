import { Outlet } from "react-router";
import Header from "../components/Header";
import Navbar from "../components/NavBar";

export default function RootLayout() {
  return (
    <div className="flex flex-col bg-page">
      <Navbar />
      <div className="mt-[75px] relative">
        <Header height={200} isHome={false}/>
        <div className="relative bg-page w-full h-[40px] prompt-section"></div>
        <Outlet />
      </div>
    </div>
  );
}
