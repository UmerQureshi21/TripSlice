// A clear, visible error with an optional retry, used wherever a request can fail
export default function ErrorBanner({
  message,
  onRetry,
  className = "",
}: {
  message: string;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <div
      role="alert"
      className={`w-[90%] md:w-[65%] rounded-[20px] border border-red-500 bg-red-500/10 px-[20px] py-[15px] poppins-font text-red-500 text-center flex flex-col sm:flex-row items-center justify-center gap-[12px] ${className}`}
    >
      <span>{message}</span>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="shrink-0 rounded-full border border-red-500/60 px-[14px] py-[4px] text-sm hover:bg-red-500/15 transition-colors cursor-pointer"
        >
          Try again
        </button>
      )}
    </div>
  );
}
